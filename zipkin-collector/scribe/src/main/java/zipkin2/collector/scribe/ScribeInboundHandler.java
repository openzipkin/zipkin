/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.collector.scribe;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.util.Exceptions;
import com.linecorp.armeria.common.util.SafeCloseable;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.ServiceRequestContextBuilder;
import com.linecorp.armeria.server.thrift.THttpService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static zipkin2.Call.propagateIfFatal;

@SuppressWarnings("FutureReturnValueIgnored")
// TODO: errorprone wants us to check futures before returning, but what would be a sensible check?
// Say it is somehow canceled, would we take action? Would callback.onError() be redundant?
final class ScribeInboundHandler extends ChannelInboundHandlerAdapter {

  static final Logger logger = LoggerFactory.getLogger(ScribeInboundHandler.class);

  // Headers mostly copied from https://github.com/apache/thrift/blob/master/lib/javame/src/org/apache/thrift/transport/THttpClient.java#L130
  static final RequestHeaders THRIFT_HEADERS = RequestHeaders.builder(
    HttpMethod.POST, "/internal/zipkin-thriftrpc")
    .set(HttpHeaderNames.CONTENT_TYPE, "application/x-thrift")
    .set(HttpHeaderNames.ACCEPT, "application/x-thrift")
    .set(HttpHeaderNames.USER_AGENT, "Zipkin/ScribeInboundHandler")
    .build();

  final THttpService scribeService;

  ScribeInboundHandler(ScribeSpanConsumer scribe) {
    scribeService = THttpService.of(scribe);
  }

  Map<Integer, ByteBuf> pendingResponses = new HashMap<>();
  int nextResponseIndex = 0;
  int previouslySentResponseIndex = -1;

  @Override public void channelRead(ChannelHandlerContext ctx, Object payload) {
    assert payload instanceof ByteBuf;
    HttpRequest request = HttpRequest.of(THRIFT_HEADERS, HttpData.wrap((ByteBuf) payload));
    ServiceRequestContextBuilder requestContextBuilder = ServiceRequestContext.builder(request)
      .service(scribeService)
      .alloc(ctx.alloc());

    if (ctx.executor() instanceof EventLoop) {
      requestContextBuilder.eventLoop((EventLoop) ctx.executor());
    }

    ServiceRequestContext requestContext = requestContextBuilder.build();

    final HttpResponse response;
    try (SafeCloseable unused = requestContext.push()) {
      response = HttpResponse.of(scribeService.serve(requestContext, request));
    } catch (Throwable t) {
      propagateIfFatal(t);
      exceptionCaught(ctx, t);
      return;
    }

    int responseIndex = nextResponseIndex++;

    response.aggregateWithPooledObjects(ctx.executor(), ctx.alloc()).handle((msg, t) -> {
      if (t != null) {
        exceptionCaught(ctx, t);
        return null;
      }

      try (HttpData content = msg.content()) {
        ByteBuf returned = ctx.alloc().buffer(content.length() + 4);
        returned.writeInt(content.length());
        returned.writeBytes(content.byteBuf());
        if (responseIndex == previouslySentResponseIndex + 1) {
          ctx.writeAndFlush(returned);
          previouslySentResponseIndex++;

          flushResponses(ctx);
        } else {
          pendingResponses.put(responseIndex, returned);
        }
      }

      return null;
    });
  }

  @Override public void channelInactive(ChannelHandlerContext ctx) {
    release();
  }

  @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    Exceptions.logIfUnexpected(logger, ctx.channel(), cause);

    release();
    closeOnFlush(ctx.channel());
  }

  void flushResponses(ChannelHandlerContext ctx) {
    while (!pendingResponses.isEmpty()) {
      ByteBuf response = pendingResponses.remove(previouslySentResponseIndex + 1);
      if (response == null) {
        return;
      }

      ctx.writeAndFlush(response);
      previouslySentResponseIndex++;
    }
  }

  void release() {
    pendingResponses.values().forEach(ByteBuf::release);
    pendingResponses.clear();
  }

  /**
   * Closes the specified channel after all queued write requests are flushed.
   */
  static void closeOnFlush(Channel ch) {
    if (ch.isActive()) {
      ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
  }
}
