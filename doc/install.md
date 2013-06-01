## Installation

To get going quickly, see the
[Ubuntu Quickstart](https://github.com/twitter/zipkin/blob/master/doc/ubuntu-quickstart.txt) and
[Mac Quickstart](https://github.com/twitter/zipkin/blob/master/doc/mac-quickstart.md) guides.
These will help you get Zipkin running on a single machine so that you can experiment with it.

This document explains the services and dependencies with which Zipkin
interacts, and more advanced configuration.


### Cassandra

Zipkin is most commonly used with [Cassandra](http://cassandra.apache.org/) for
the collector's storage. There is also a
[Redis plugin](https://github.com/twitter/zipkin/blob/master/doc/redis.md) and
we'd like to see support for other databases.

1. See Cassandra's <a href="http://cassandra.apache.org/">site</a> for instructions on how to start a cluster.
2. Use the Zipkin Cassandra schema attached to this project. You can create the schema with the following command:
`cassandra-cli -host localhost -port 9160 -f zipkin-cassandra/src/schema/cassandra-schema.txt`

### ZooKeeper
Zipkin can use ZooKeeper for coordination. That's where we store the server side sample rate and register the servers.

1. See ZooKeeper's <a href="http://zookeeper.apache.org/">site</a> for instructions on how to install it.

### Scribe
<a href="https://github.com/facebook/scribe">Scribe</a> is the logging
framework we use at Twitter to transport the trace data. There are several other
ways to tell Zipkin what trace data to collect; in particular, if you are just
trying out Zipkin you can skip this step entirely and point the ZipkinTracer
directly at the collector.

To use Scribe with Zipkin, you need to set up a network store that points to
the Zipkin collector daemon. A Scribe store for Zipkin might look something
like this:

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

If you don't want to hardcode the IP address of your collector there are a few
options. One is to use an internal DNS entry for the collectors so that you only
have one place to change the addresses when you add or remove collectors.
Alternatively, if you want to get all fancy you can use a
[modified version of Scribe](https://github.com/traviscrawford/scribe) that
picks up the collectors via ZooKeeper. When each collector starts up it adds
itself to ZooKeeper and when a collector shuts down it is automatically
removed. The modified Scribe gets notified when the set of collectors change.
To use this mode you change `remote_host` in the configuration to
`zk://zookeeper-hostname:2181/scribe/zipkin` or something similar.

We're hoping that others might add non-Scribe transports for the tracing data;
there is no reason why Scribe has to be the only one.

### Zipkin servers
We developed Zipkin with
[Scala 2.9.1](http://www.scala-lang.org/downloads),
[SBT 0.11.2](http://www.scala-sbt.org/download.html), and JDK7.

The [Ubuntu Quickstart](https://github.com/twitter/zipkin/blob/master/doc/ubuntu-quickstart.txt)
and [Mac Quickstart](https://github.com/twitter/zipkin/blob/master/doc/mac-quickstart.md)
guides explain how to set up and run the collector and query services.

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

