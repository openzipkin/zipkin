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

import com.linecorp.armeria.common.CommonPools;
import com.linecorp.armeria.common.util.EventLoopGroups;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import java.net.InetSocketAddress;

class NettyScribeServer {

  private final int port;
  private final ScribeSpanConsumer scribe;

  volatile EventLoopGroup bossGroup;
  volatile Channel channel;

  NettyScribeServer(int port, ScribeSpanConsumer scribe) {
    this.port = port;
    this.scribe = scribe;
  }

  void start() {
    bossGroup = EventLoopGroups.newEventLoopGroup(1);
    EventLoopGroup workerGroup = CommonPools.workerGroup();

    ServerBootstrap b = new ServerBootstrap();
    try {
      channel = b.group(bossGroup, workerGroup)
        .channel(EventLoopGroups.serverChannelType(bossGroup))
        .childHandler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            ch.pipeline().addLast(new ScribeInboundHandler(scribe));
          }
        })
        // Uses same value as the previously used swift library for consistency.
        .childOption(ChannelOption.SO_BACKLOG, 1024)
        .bind(port)
        .syncUninterruptibly()
        .channel();
    } catch (Throwable t) {
      throw new RuntimeException("Could not start scribe server.", t);
    }
  }

  void close() {
    if (channel == null) return;
    channel.close();
    bossGroup.shutdownGracefully();
  }

  boolean isRunning() {
    return channel != null && channel.isActive();
  }

  int port() {
    if (channel == null) return 0;
    return ((InetSocketAddress) channel.localAddress()).getPort();
  }
}
