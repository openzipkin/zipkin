/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import com.linecorp.armeria.unsafe.ByteBufHttpData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
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

  enum ReadState {
    HEADER,
    PAYLOAD
  }

  CompositeByteBuf pending;
  ReadState state;
  int nextFrameSize;

  Map<Integer, ByteBuf> pendingResponses = new HashMap<>();
  int nextResponseIndex = 0;
  int previouslySentResponseIndex = -1;

  @Override public void channelActive(ChannelHandlerContext ctx) {
    pending = ctx.alloc().compositeBuffer();
    state = ReadState.HEADER;
  }

  @Override public void channelRead(final ChannelHandlerContext ctx, Object msg) {
    if (pending == null) return; // Already closed (probably due to an exception).

    assert msg instanceof ByteBuf;
    ByteBuf buf = (ByteBuf) msg;
    pending.addComponent(true, buf);

    switch (state) {
      case HEADER:
        maybeReadHeader(ctx);
        break;
      case PAYLOAD:
        maybeReadPayload(ctx);
        break;
    }
  }

  @Override public void channelInactive(ChannelHandlerContext ctx) {
    release();
  }

  @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    Exceptions.logIfUnexpected(logger, ctx.channel(), cause);

    release();
    closeOnFlush(ctx.channel());
  }

  void maybeReadHeader(ChannelHandlerContext ctx) {
    if (pending.readableBytes() < 4) return;

    nextFrameSize = pending.readInt();
    state = ReadState.PAYLOAD;
    maybeReadPayload(ctx);
  }

  void maybeReadPayload(ChannelHandlerContext ctx) {
    if (pending.readableBytes() < nextFrameSize) return;

    ByteBuf payload = ctx.alloc().buffer(nextFrameSize);
    pending.readBytes(payload, nextFrameSize);
    pending.discardSomeReadBytes();

    state = ReadState.HEADER;

    HttpRequest request = HttpRequest.of(THRIFT_HEADERS, new ByteBufHttpData(payload, true));
    ServiceRequestContextBuilder requestContextBuilder = ServiceRequestContextBuilder.of(request)
      .service(scribeService)
      .alloc(ctx.alloc());

    if (ctx.executor() instanceof EventLoop) {
      requestContextBuilder.eventLoop((EventLoop) ctx.executor());
    }

    ServiceRequestContext requestContext = requestContextBuilder.build();

    final HttpResponse response;
    try (SafeCloseable unused = requestContext.push()) {
      response = scribeService.serve(requestContext, request);
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

      HttpData content = msg.content();
      ByteBuf returned = ctx.alloc().buffer(content.length() + 4);
      returned.writeInt(content.length());

      if (content instanceof ByteBufHolder) {
        ByteBuf buf = ((ByteBufHolder) content).content();
        try {
          returned.writeBytes(buf);
        } finally {
          buf.release();
        }
      } else {
        returned.writeBytes(content.array(), content.offset(), content.length());
      }

      if (responseIndex == previouslySentResponseIndex + 1) {
        ctx.writeAndFlush(returned);
        previouslySentResponseIndex++;

        flushResponses(ctx);
      } else {
        pendingResponses.put(responseIndex, returned);
      }

      return null;
    });
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
    if (pending != null) {
      pending.release();
      pending = null;
    }

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
