package zipkin2.collector.scribe;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ScribeInboundHandler extends ChannelInboundHandlerAdapter {

  static final Logger logger = LoggerFactory.getLogger(ScribeInboundHandler.class);

  // Headers mostly copied from https://github.com/apache/thrift/blob/master/lib/javame/src/org/apache/thrift/transport/THttpClient.java#L130
  static final HttpHeaders THRIFT_HEADERS = HttpHeaders.of(
    HttpMethod.POST, "/internal/zipkin-thriftrpc")
    .set(HttpHeaderNames.CONTENT_TYPE, "application/x-thrift")
    .set(HttpHeaderNames.ACCEPT, "application/x-thrift")
    .set(HttpHeaderNames.USER_AGENT, "Zipkin/ScribeInboundHandler")
    .asImmutable();

  // TODO(anuraaga): Actually implement the service.
  static final THttpService THRIFT_SERVICE = THttpService.of(new Object());

  enum ReadState {
    HEADER,
    PAYLOAD
  }

  CompositeByteBuf pending;
  ReadState state;
  int nextFrameSize;

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    pending = ctx.alloc().compositeBuffer();
    state = ReadState.HEADER;
  }

  @Override
  public void channelRead(final ChannelHandlerContext ctx, Object msg) {
    if (pending == null) {
      // Already closed (probably due to an exception).
      return;
    }

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

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    release();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    logger.warn("Unexpected error handling connection.", cause);

    release();
    closeOnFlush(ctx.channel());
  }

  void maybeReadHeader(ChannelHandlerContext ctx) {
    if (pending.readableBytes() < 4) {
      return;
    }
    nextFrameSize = readFrameSize(pending);
    state = ReadState.PAYLOAD;
    maybeReadPayload(ctx);
  }

  void maybeReadPayload(ChannelHandlerContext ctx) {
    if (pending.readableBytes() < nextFrameSize) {
      return;
    }

    ByteBuf payload = ctx.alloc().buffer(nextFrameSize);
    pending.readBytes(payload, nextFrameSize);

    state = ReadState.HEADER;

    HttpRequest request = HttpRequest.of(THRIFT_HEADERS, new ByteBufHttpData(payload, true));
    ServiceRequestContext requestContext = ServiceRequestContextBuilder.of(request)
      .service(THRIFT_SERVICE)
      .build();

    final HttpResponse response;
    try {
      response = THRIFT_SERVICE.serve(requestContext, request);
    } catch (Exception e) {
      logger.warn("Unexpected exception servicing thrift request. "
        + "This usually indicates a bug in armeria's thrift implementation.", e);
      exceptionCaught(ctx, e);
      return;
    }

    response.aggregateWithPooledObjects(ctx.executor(), ctx.alloc()).handle((msg, t) -> {
      if (t != null) {
        logger.warn("Unexpected exception reading thrift response. "
          + "This usually indicates a bug in armeria's thrift implementation.", t);
        exceptionCaught(ctx, t);
        return null;
      }

      HttpData content = msg.content();
      final ByteBuf returned = ctx.alloc().buffer(msg.content().length() + 4);

      writeFrameSize(msg.content().length(), returned);

      if (content instanceof ByteBufHolder) {
        returned.writeBytes(((ByteBufHolder) content).content());
      } else {
        returned.writeBytes(content.array(), content.offset(), content.length());
      }

      ctx.writeAndFlush(returned);

      return null;
    });
  }

  void release() {
    if (pending != null) {
      pending.release();
      pending = null;
    }
  }

  /**
   * Closes the specified channel after all queued write requests are flushed.
   */
  static void closeOnFlush(Channel ch) {
    if (ch.isActive()) {
      ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
  }

  static int readFrameSize(ByteBuf buf) {
    return (buf.readByte() & 255) << 24
      | (buf.readByte() & 255) << 16
      | (buf.readByte() & 255) << 8
      | buf.readByte() & 255;
  }

  static void writeFrameSize(int frameSize, ByteBuf buf) {
    buf.writeByte((byte)(255 & frameSize >> 24));
    buf.writeByte((byte)(255 & frameSize >> 16));
    buf.writeByte((byte)(255 & frameSize >> 8));
    buf.writeByte((byte)(255 & frameSize));
  }
}
