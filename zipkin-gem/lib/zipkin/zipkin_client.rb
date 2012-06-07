# Copyright 2012 Twitter Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This file is the Client For interacting with the Zipkin Thrift service. All of the ruby files
# related to using this code are generated using thrift from bbb.thrift and scribe.thrift
# I use a shortcut file to download and generate the ruby code. See script/update_thrifts for more info

require 'thrift'
require 'finagle-thrift'
require 'zookeeper'

module Zipkin
  module ZipkinClient

    # The node location in zookeeper that stores the cassandra location
    NODE_PATH = "/twitter/service/zipkin/query"

    def self.with_transport(opts = {})
      # Open a connection to a thrift server, do something, and close the connection

      begin

        if opts[:use_local_server]
          # If we're running local (development mode) return a set value
          host = "localhost"
          port = 9149

          # Create the connection to the local b3 query_daemon which uses different
          # transport mechanism
          socket = Thrift::Socket.new(host, port)
          transport = Thrift::BufferedTransport.new(socket)
        else
          # Get the host and port of the location of the query service from zookeeper
          zk_host = opts[:zk_host] || "localhost"
          zk_port = opts[:zk_port] || 2181

          host, port = ZipkinClient::get_query_service(zk_host, zk_port, opts)

          # Create the connection to the b3 query_daemon
          socket = Thrift::Socket.new(host, port)
          buffered_tp = Thrift::BufferedTransport.new(socket)
          transport = Thrift::FramedTransport.new(buffered_tp)
        end

        protocol = Thrift::BinaryProtocol.new(transport)
        client = ThriftClient.new(Zipkin::ZipkinQuery::Client, host + ':' + port.to_s, :retries => 0, :timeout => 60)

        # set up tracing for the client we use to talk to the query daemon
        client_id = FinagleThrift::ClientId.new(:name => "zipkin.prod")
        FinagleThrift.enable_tracing!(client, client_id, "zipkin")

        begin
          transport.open
          yield(client)
        ensure
          transport.close
        end
      rescue ZookeeperExceptions::ZookeeperException::ConnectionClosed => ze
        "Could not connect to zookeeper at #{opts[:zk_host]}:#{opts[:zk_port]}"
      end

    end

    def self.get_query_service(zk_host, zk_port, opts={})
      # Takes either:
      # - ZooKeeper config options that map to a Zipkin Query server set OR
      # - Direct host/port of a Query daemon

      if opts[:skip_zookeeper]
        return [ opts[:zipkin_query_host], opts[:zipkin_query_port] ]
      end

      node_path = opts[:node_path] || NODE_PATH

      # TODO: throw error if it fails
      zk = Zookeeper.new("#{zk_host}:#{zk_port}")

      begin
        # TODO: handle errors here
        children = zk.get_children(:path => node_path)
        node_key = children[:children][0]

        # TODO: throw errors
        node = zk.get(:path => "#{node_path}/#{node_key}")
      ensure
        zk.close() if zk
      end

      # Deserialize the result
      d = Thrift::Deserializer.new
      si = d.deserialize(Twitter::Thrift::ServiceInstance.new, node[:data])

      # Return the host and port
      [si.serviceEndpoint.host, si.serviceEndpoint.port]
    end
  end
end