package zipkin2.collector.scribe;

import com.linecorp.armeria.common.CommonPools;
import com.linecorp.armeria.common.util.EventLoopGroups;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;

class NettyScribeServer {

  private final int port;

  volatile EventLoopGroup bossGroup;
  volatile Channel channel;

  NettyScribeServer(int port) {
    this.port = port;
  }

  void start() {
    bossGroup = EventLoopGroups.newEventLoopGroup(1);
    EventLoopGroup workerGroup = CommonPools.workerGroup();

    ServerBootstrap b = new ServerBootstrap();
    channel = b.group(bossGroup, workerGroup)
      .channel(EventLoopGroups.serverChannelType(bossGroup))
      .childHandler(new ScribeInboundHandler())
      .childOption(ChannelOption.AUTO_READ, true)
      .childOption(ChannelOption.SO_KEEPALIVE, true)
      .bind(port)
      .channel();
  }

  void close() {
    channel.close();
    bossGroup.shutdownGracefully();
  }

  boolean isRunning() {
    return channel.isActive();
  }
}
