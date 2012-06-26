![Zipkin (doc/zipkin-logo-200x119.jpg)](https://github.com/twitter/zipkin/raw/master/doc/zipkin-logo-200x119.jpg)

Zipkin is a distributed tracing system that helps us gather timing data for all the disparate services at Twitter.
It manages both the collection and lookup of this data through a Collector and a Query service.
We closely modelled Zipkin after the <a href="http://research.google.com/pubs/pub36356.html">Google Dapper</a> paper. Follow <a href="https://twitter.com/zipkinproject">@zipkinproject</a> for updates. [![Build Status](https://secure.travis-ci.org/twitter/zipkin.png)](http://travis-ci.org/twitter/zipkin)

## Why distributed tracing?
Collecting traces helps developers gain deeper knowledge about how certain requests perform in a distributed system.
Let's say we're having problems with user requests timing out. We can look up traced requests that timed out and display
it in the web UI. We'll be able to quickly find the service responsible for adding the unexpected response time.
If the service has been annotated adequately we can also find out where in that service the issue is happening.

![Screnshot of the Zipkin web UI (doc/web-screenshot.png)](https://github.com/twitter/zipkin/raw/master/doc/web-screenshot.png)

## Architecture
These are the components that make up a fully fledged tracing system.

![Zipkin Architecture (doc/architecture-0.png)](https://github.com/twitter/zipkin/raw/master/doc/architecture-0.png)

### Instrumented libraries
Tracing information is collected on each host using the instrumented libraries and sent to Zipkin. 
When the host makes a request to another service, it passes a few tracing identifers along with the request so we can later tie the data together.

![Zipkin Instrumentation architecture (doc/architecture-1.png)](https://github.com/twitter/zipkin/raw/master/doc/architecture-1.png)

We have instrumented the libraries below to trace requests and to pass the required identifiers to the other services called in the request.

##### Finagle
> Finagle is an asynchronous network stack for the JVM that you can use to build asynchronous Remote Procedure Call (RPC) clients and servers in Java, Scala, or any JVM-hosted language.

<a href="https://github.com/twitter/finagle">Finagle</a> is used heavily inside of Twitter and it was a natural point to include tracing support. So far we have client/server support for Thrift and HTTP as well as client only support for Memcache and Redis.

To set up a Finagle server in Scala, just do the following.
Adding tracing is as simple as adding <a href="https://github.com/twitter/finagle/tree/master/finagle-zipkin">finagle-zipkin</a> as a dependency and a `tracerFactory` to the ServerBuilder. 

```scala
ServerBuilder()
  .codec(ThriftServerFramedCodec())
  .bindTo(serverAddr)
  .name("servicename")
  .tracerFactory(ZipkinTracer())
  .build(new SomeService.FinagledService(queryService, new TBinaryProtocol.Factory()))
```

The tracing setup for clients is similar. When you've specified the Zipkin tracer as above a small sample of your requests will be traced automatically. We'll record when the request started and ended, services and hosts involved.

In case you want to record additional information you can add a custom annotation in your code.

```scala
Trace.record("starting that extremely expensive computation")
```

The line above will add an annotation with the string attached to the point in time when it happened. You can also add a key value annotation. It could look like this:

```scala
Trace.recordBinary("http.response.code", "500")
```

##### Ruby Thrift
There's a <a href="https://rubygems.org/gems/finagle-thrift">gem</a> we use to trace requests. In order to push the tracer and generate a trace id on a request you can use that gem in a RackHandler. See <a href="https://github.com/twitter/zipkin/blob/master/zipkin-web/config/application.rb">zipkin-web</a> for an example of where we trace the tracers.

For tracing client calls from Ruby we rely on the Twitter <a href="https://github.com/twitter/thrift_client">Ruby Thrift client</a>. See below for an example on how to wrap the client.

```ruby
client = ThriftClient.new(SomeService::Client, "127.0.0.1:1234")
client_id = FinagleThrift::ClientId.new(:name => "service_example.sample_environment")
FinagleThrift.enable_tracing!(client, client_id), "service_name")
```

##### Querulous
<a href="https://github.com/twitter/querulous">Querulous</a> is a Scala library for interfacing with SQL databases. The tracing includes the timings of the request and the SQL query performed.

##### Cassie
<a href="https://github.com/twitter/cassie">Cassie</a> is a Finagle based Cassandra client library. You set the tracer in Cassie pretty much like you would in Finagle, but in Cassie you set it on the KeyspaceBuilder.

```scala
cluster.keyspace(keyspace).tracerFactory(ZipkinTracer())
```

### Transport
We use Scribe to transport all the traces from the different services to Zipkin and Hadoop.
Scribe was developed by Facebook and it's made up of a daemon that can run on each server in your system.
It listens for log messages and routes them to the correct receiver depending on the category.

### Zipkin collector daemon
Once the trace data arrives at the Zipkin collector daemon we check that it's valid, store it and the index it for lookups.

### Storage
We settled on Cassandra for storage. It's scalable, has a flexible schema and is heavily used within Twitter. We did try to make this component pluggable though, so should not be hard to put in something else here.

### Zipkin query daemon
Once the data is stored and indexed we need a way to extract it. This is where the query daemon comes in, providing the users with a simple Thrift api for finding and retrieving traces. See <a href="https://github.com/twitter/zipkin/blob/master/zipkin-thrift/src/main/thrift/zipkin.thrift">the Thrift file</a>.

### UI
Most of our users access the data via our UI. It's a Rails app that uses <a href="http://d3js.org/">D3</a> to visualize the trace data. Note that there is no built in authentication in the UI.

## Modules
![Modules (doc/modules.png)](https://github.com/twitter/zipkin/raw/master/doc/modules.png)

## Installation

### Cassandra
Zipkin relies on Cassandra for storage. So you will need to bring up a Cassandra cluster.

1. See Cassandra's <a href="http://cassandra.apache.org/">site</a> for instructions on how to start a cluster.
2. Use the Zipkin Cassandra schema attached to this project. You can create the schema with the following command.
`bin/cassandra-cli -host localhost -port 9160 -f zipkin-server/src/schema/cassandra-schema.txt`

### ZooKeeper
Zipkin uses ZooKeeper for coordination. That's where we store the server side sample rate and register the servers.

1. See ZooKeeper's <a href="http://zookeeper.apache.org/">site</a> for instructions on how to install it.

### Scribe
<a href="https://github.com/facebook/scribe">Scribe</a> is the logging framework we use to transport the trace data.
You need to set up a network store that points to the Zipkin collector daemon.

A Scribe store for Zipkin might look something like this.

    <store>
      category=zipkin
      type=network
      remote_host=zk://zookeeper-hostname:2181/scribe/zipkin
      remote_port=9410
      use_conn_pool=yes
      default_max_msg_before_reconnect=50000
      allowable_delta_before_reconnect=12500
      must_succeed=no
    </store>

Note that the above uses the Twitter version of Scribe with support for using ZooKeeper to find the hosts to send the category to. You can also use a DNS entry for the collectors or something similar.

### Zipkin servers
We've developed Zipkin with <a href="http://www.scala-lang.org/downloads">Scala 2.9.1</a>, <a href="http://www.scala-sbt.org/download.html">SBT 0.11.2</a>, and JDK7.

1. `git clone https://github.com/twitter/zipkin.git`
1. `cd zipkin`
1. `cp zipkin-scribe/config/collector-dev.scala zipkin-scribe/config/collector-prod.scala`
1. `cp zipkin-server/config/query-dev.scala zipkin-server/config/query-prod.scala`
1. Modify the configs above as needed. Pay particular attention to ZooKeeper and Cassandra server entries.
1. `bin/sbt update package-dist` (This downloads SBT 0.11.2 if it doesn't already exist)
1. `scp dist/zipkin*.zip [server]`
1. `ssh [server]`
1. `unzip zipkin*.zip`
1. `mkdir -p /var/log/zipkin`
1. `zipkin-scribe/scripts/collector.sh -f zipkin-scribe/config/collector-prod.scala`
1. `zipkin-server/scripts/query.sh -f zipkin-server/config/query-prod.scala`

You can also run the collector and query services through SBT.

To run the Scribe collector service: `bin/sbt 'project zipkin-scribe' 'run -f zipkin-scribe/config/collector-dev.scala'`

To run the query service: `bin/sbt 'project zipkin-server' 'run -f zipkin-server/config/query-dev.scala'`

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

## Running a Hadoop job
It's possible to setup Scribe to log into Hadoop. If you do this you can generate various reports from the data
that is not easy to do on the fly in Zipkin itself.

We use a library called <a href="http://github.com/twitter/scalding">Scalding</a> to write Hadoop jobs in Scala.

1. To run a Hadoop job first make the fat jar.
    `sbt 'project zipkin-hadoop' compile assembly`
2. Change scald.rb to point to the hostname you want to copy the jar to and run the job from.
3. Update the version of the jarfile in scald.rb if needed.
3. You can then run the job using our scald.rb script.
    `./scald.rb --hdfs com.twitter.zipkin.hadoop.[classname] --date yyyy-mm-ddThh:mm yyyy-mm-ddThh:mm --output [dir]`

## How to instrument a library
We have instrumented a few libraries and protocols, but we hope to get some help instrumenting a few more. 
Before we start we need to know a few things about how we structure the tracing data.

* Annotation - includes a value, timestamp, and host
* Span - a set of annotations that correspond to a particular RPC
* Trace - a set of spans that share a single root span

The above is used to send the tracing data to Zipkin. You can find these and more described <a href="https://github.com/twitter/zipkin/blob/master/zipkin-thrift/src/main/thrift/zipkinCore.thrift">here</a>

Another important part of the tracing is the light weight header we use to pass information between the traced services.
The tracing header consists of the following:

* Trace Id - identifies the whole trace
* Span Id - identifies an individual request
* Optional Parent Span Id - Added if this request was made as part of another request
* Sampled boolean - tells us if we should log the tracing data or not

Now that we know a bit about the data types, let's take a step by step look at how the instrumentation works. 
The example below will describe how the Http tracing in Finagle works. Other libraries and protocols will of course be different, but the general principle should be the same.

### Server side
1. Check if there are any tracing headers in the incoming request. If there is, we adopt ids associated with that for this request. If not, we generate a new trace id, span id and decide if we should sample or not. See <a href="https://github.com/twitter/finagle/blob/master/finagle-http/src/main/scala/com/twitter/finagle/http/Codec.scala">HttpServerTracingFilter</a> for an example of this.

1. If the current request is to be sampled we gather information such as service name, hostname, span name (http get/put for example) and the actual annotations. We create a "server received" annotation when we get the request and a "server send" one when we are done processing and are just about to send the result. Again, you can see this in <a href="https://github.com/twitter/finagle/blob/master/finagle-http/src/main/scala/com/twitter/finagle/http/Codec.scala">HttpServerTracingFilter</a>.

1. The tracing data created is passed to whatever tracer was set on the ServerBuilder. This could be ConsoleTracer for debugging for example, but in our case we'll assume it's <a href="https://github.com/twitter/finagle/tree/master/finagle-zipkin">ZipkinTracer</a>. When tracing data is received by the ZipkinTracer it aggregates them by span id.

1. Once the ZipkinTracer receives an "end of span" event, something like a "server received" annotation or a timeout it will send the aggregated data as a Thrift struct to Scribe. If no such event happens it will eventually send the data anyway. We're open to adding other ways of transporting the data, for us Thrift and Scribe made sense but perhaps JSON and Http will work better for some.

### Client side
1. Before making the request, figure out if we are part of a trace already. It could be that this client is used within a server for example. That server could be processing a request and therefore already has a trace id assigned. We reuse that trace id, but we generate a new span id for this new request. We also set the parent span id to the previous span id, if available. You can see some of this <a href="https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/tracing/TracingFilter.scala">here</a> and <a href="https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/tracing/Trace.scala">here</a>.

1. Similar to on the server side we have a <a href="https://github.com/twitter/finagle/blob/master/finagle-http/src/main/scala/com/twitter/finagle/http/Codec.scala">HttpClientTracingFilter</a> that adds the tracing headers to the outgoing http request.

1. We also generate the appropriate annotations, such as "client send" before the request and "client receive" after we receive a reply from the server.

1. Similar to the server side the data reaches the ZipkinTracer that sends it off to Zipkin.


## Mailing lists
There are two mailing lists you can use to get in touch with other users and developers.

Users: https://groups.google.com/group/zipkin-user

Developers: https://groups.google.com/group/zipkin-dev

## Issues
Noticed a bug? Please add an issue here. https://github.com/twitter/zipkin/issues

## Contributions
Contributions are very welcome! Please create a pull request on github and we'll look at it as soon as possible.

Try to make the code in the pull request as focused and clean as possible, stick as close to our code style as you can.

If the pull request is becoming too big we ask that you split it into smaller ones.

Areas where we'd love to see contributions include: adding tracing to more libraries and protocols, interesting reports generated with Hadoop from the trace data, extending collector to support more transports and storage systems and other ways of visualizing the data in the web UI.

## Versioning
We intend to use the <a href="http://semver.org/">semver</a> style versioning.

## Authors
Thanks to everyone below for making Zipkin happen!

Zipkin server
 * Johan Oskarsson: <a href="https://twitter.com/skr">@skr</a>
 * Franklin Hu: <a href="https://twitter.com/thisisfranklin">@thisisfranklin</a>
 * Ian Ownbey: <a href="https://twitter.com/iano">@iano</a>

Zipkin UI
 * Franklin Hu: <a href="https://twitter.com/thisisfranklin">@thisisfranklin</a>
 * Bill Couch: <a href="https://twitter.com/couch">@couch</a>
 * David McLaughlin: <a href="https://twitter.com/dmcg">@dmcg</a>
 * Chad Rosen: <a href="https://twitter.com/zed">@zed</a>

Instrumentation
 * Marius Eriksen: <a href="https://twitter.com/marius">@marius</a>
 * Arya Asemanfar: <a href="https://twitter.com/a_a">@a_a</a>

## License
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
