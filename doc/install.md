## Installation

### Cassandra
Zipkin relies on Cassandra for storage. So you will need to bring up a Cassandra cluster.

1. See Cassandra's <a href="http://cassandra.apache.org/">site</a> for instructions on how to start a cluster.
2. Use the Zipkin Cassandra schema attached to this project. You can create the schema with the following command.
`bin/cassandra-cli -host localhost -port 9160 -f zipkin-cassandra/src/schema/cassandra-schema.txt`

### ZooKeeper
Zipkin uses ZooKeeper for coordination. That's where we store the server side sample rate and register the servers.

1. See ZooKeeper's <a href="http://zookeeper.apache.org/">site</a> for instructions on how to install it.

### Scribe
<a href="https://github.com/facebook/scribe">Scribe</a> is the logging framework we use to transport the trace data.
You need to set up a network store that points to the Zipkin collector daemon. If you are just trying out Zipkin you can skip this step entirely and point the ZipkinTracer directly at the collector.

A Scribe store for Zipkin might look something like this.

    <store>
      category=zipkin
      type=network
      remote_host=123.123.123.123
      remote_port=9410
      use_conn_pool=yes
      default_max_msg_before_reconnect=50000
      allowable_delta_before_reconnect=12500
      must_succeed=no
    </store>

If you don't want to hardcode the IP address of your collector there are a few options. 

You can use an internal DNS entry for the collectors, that way you only have one place to change the addresses when you add or remove collectors. 

If you want to get all fancy you can use a modified version of <a href="Scribe">https://github.com/traviscrawford/scribe</a> that picks up the collectors via ZooKeeper. When each collector starts up it adds itself to ZooKeeper and when a collector shuts down it is automatically removed. The modified Scribe gets notified when the set of collectors change. To use this mode you change remote_host in the configuration to zk://zookeeper-hostname:2181/scribe/zipkin or something similar.

We're hoping that others might add non-Scribe transports for the tracing data; there is no reason why Scribe has to be the only one.

### Zipkin servers
We've developed Zipkin with <a href="http://www.scala-lang.org/downloads">Scala 2.9.1</a>, <a href="http://www.scala-sbt.org/download.html">SBT 0.11.2</a>, and JDK7.

1. `git clone https://github.com/twitter/zipkin.git`
1. `cd zipkin`
1. `cp zipkin-collector-service/config/collector-dev.scala zipkin-collector-service/config/collector-prod.scala`
1. `cp zipkin-query-service/config/query-dev.scala zipkin-query-service/config/query-prod.scala`
1. Modify the configs above as needed. Pay particular attention to ZooKeeper and Cassandra server entries.
1. `bin/sbt update package-dist` (This downloads SBT 0.11.2 if it doesn't already exist)
1. `scp dist/zipkin*.zip [server]`
1. `ssh [server]`
1. `unzip zipkin*.zip`
1. `mkdir -p /var/log/zipkin`
1. `zipkin-collector-service/src/scripts/collector.sh -f zipkin-collector-service/config/collector-prod.scala`
1. `zipkin-query-service/src/scripts/query.sh -f zipkin-query-service/config/query-prod.scala`

You can also run the collector and query services through SBT.

To run the Scribe collector service: `bin/sbt 'project zipkin-collector-service' 'run -f zipkin-collector-service/config/collector-dev.scala'` or `bin/collector`

To run the query service: `bin/sbt 'project zipkin-query-service' 'run -f zipkin-query-service/config/query-dev.scala'` or `bin/query`

### Zipkin UI
The UI is a standard Rails 3 app.

1. Update config with your ZooKeeper server. This is used to find the query daemons.
2. Deploy to a suitable Rails 3 app server. For testing you can simply do
```
  bundle install &&
  bundle exec rails server.
```

#### zipkin-tracer gem
The `zipkin-tracer` gem adds tracing to a Rails application through the use of a Rack Handler.
In `config.ru`:

```ruby
  use ZipkinTracer::RackHandler
  run <YOUR_APPLICATION>
```

If the application's static assets are served through Rails, those requests will be traced.

