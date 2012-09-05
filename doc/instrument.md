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

