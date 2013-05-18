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
Adding tracing is as simple as adding <a href="https://github.com/twitter/finagle/tree/master/finagle-zipkin">finagle-zipkin</a> as a dependency and a `tracer` to the ServerBuilder.

```scala
ServerBuilder()
  .codec(ThriftServerFramedCodec())
  .bindTo(serverAddr)
  .name("servicename")
  .tracer(ZipkinTracer.mk())
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
cluster.keyspace(keyspace).tracer(ZipkinTracer.mk())
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
Once the data is stored and indexed we need a way to extract it. This is where the query daemon comes in, providing the users with a simple Thrift api for finding and retrieving traces. See <a href="https://github.com/twitter/zipkin/blob/master/zipkin-thrift/src/main/thrift/zipkinQuery.thrift">the Thrift file</a>.

### UI
Most of our users access the data via our UI. It's a Rails app that uses <a href="http://d3js.org/">D3</a> to visualize the trace data. Note that there is no built in authentication in the UI.

## Modules
![Modules (doc/modules.png)](https://github.com/twitter/zipkin/raw/master/doc/modules.png)

