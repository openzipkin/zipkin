## Installation

### Quickstart

[Scala 2.9](http://www.scala-lang.org/downloads) (JDK6 or JDK7) is required.

Install with `git clone https://github.com/twitter/zipkin.git && cd zipkin` or
[download](https://github.com/twitter/zipkin/archive/master.zip) Zipkin to your
preferred directory and unzip.

Run (in separate bash windows):

    bin/collector
    bin/query
    bin/web

Now you can access the Zipkin UI at http://localhost:8080/

### Advanced Setup

By default, Zipkin runs on SQLite because it doesn't require additional setup.
However, Zipkin is typically used with other databases (most often Cassandra)
in production. For a quickstart with Cassandra, see the
[Ubuntu Quickstart](https://github.com/twitter/zipkin/blob/master/doc/ubuntu-quickstart.txt) and
[Mac Quickstart](https://github.com/twitter/zipkin/blob/master/doc/mac-quickstart.md) guides.
These will help you get a somewhat more advanced Zipkin installation running on
a single machine so that you can experiment with it.

For larger deployments, Zipkin works with several other services, and you may
want to apply additional configuration. Some of those services and
configurations are described below.

### Feeding trace data to Zipkin

The next step after installation is to collect trace data to view in Zipkin. To
do this, you must interface with the collector daemon to record trace data.
There are several libraries to make this easier to do in different
environments. Twitter uses
[Finagle](https://github.com/twitter/finagle/tree/master/finagle-zipkin);
external libraries (currently for Python, REST, node, and Java) are listed in the
[wiki](https://github.com/twitter/zipkin/wiki#external-projects-that-use-zipkin);
and there is also a [Ruby gem](https://rubygems.org/gems/finagle-thrift) and
[Ruby Thrift client](https://github.com/twitter/thrift_client).

### Running the Daemons with Other Databases

The default Zipkin collector configuration is
`zipkin-collector-service/config/collector-dev.scala` and the default Zipkin
query configuration is `zipkin-query-service/config/query-dev.scala`. These
are set up to work with SQLite by default. To use Zipkin with a different SQL
database, we recommend changing those configurations as described in the
[SQL guide](https://github.com/twitter/zipkin/blob/master/doc/sql-databases.md).

For NoSQL databases, you can pass the name of your preferred database to the
collector and query daemons. For example, with Cassandra:

    bin/collector cassandra
    bin/query cassandra
    bin/web

### Storage: Cassandra

Zipkin runs on SQLite by default to make it easier to test out of the box.
Running Zipkin on SQLite requires no additional setup. However, in production,
Zipkin is most commonly used with [Cassandra](http://cassandra.apache.org/) for
the collector's storage. There are also plugins for
[Redis](https://github.com/twitter/zipkin/blob/master/doc/redis.md), HBase, and
[other SQL databases](https://github.com/twitter/zipkin/blob/master/doc/sql-databases.md).

1. See Cassandra's <a href="http://cassandra.apache.org/">site</a> for instructions on how to start a cluster.
2. Use the Zipkin Cassandra schema attached to this project. You can create the schema with the following command:
`cassandra-cli -host localhost -port 9160 -f zipkin-cassandra/src/schema/cassandra-schema.txt`

### Coordination: ZooKeeper

Zipkin can use ZooKeeper for coordination. That's where we store the server side sample rate and register the servers.

1. See ZooKeeper's <a href="http://zookeeper.apache.org/">site</a> for instructions on how to install it.

### Collecting Data: Scribe

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

### Zipkin UI

To develop on the ui start it with
```
bin/web.sh
```

To run zipkin ui on a server do
```
  sbt zipkin-web/package-dist
  cd zipkin-web/dist/zipkin-web
  java -cp libs/ -jar zipkin-web-1.2.0-SNAPSHOT.jar -zipkin.web.resourcesRoot=resources/
```

For a list of available options do
```
java -cp libs/ -jar zipkin-web-1.2.0-SNAPSHOT.jar -h
```

#### zipkin-tracer gem
The `zipkin-tracer` gem adds tracing to a Rails application through the use of a Rack Handler.
In `config.ru`:

```ruby
  use ZipkinTracer::RackHandler
  run <YOUR_APPLICATION>
```

If the application's static assets are served through Rails, those requests will be traced.

