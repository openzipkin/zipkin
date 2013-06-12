# Zipkin Collection API
This document is an attempt to describe how tracing data gets
submitted and collected by zipkin.  It's meant to provide a high-level
architectural overview for implementers of client-side tracing
libraries, as well as new zipkin collection modules.

## Zipkin Collector
The zipkin collector is a generic interface for getting traces into
zipkin.  There are currently two concrete implementations included in
the zipkin distribution: zipkin-collector-scribe which is the
production version used by twitter, and zipkin-kafka, which is an
experimental collector that uses kafka.  Additionally there are a few
third-party proxies for collecting zipkin traces such as
[brave-zipkin-spancollector](https://github.com/kristofa/brave-zipkin-spancollector)
and [RESTkin](https://github.com/racker/restkin).

## Thrift
Zipkin primarily uses [thrift](http://thrift.apache.org) as it's RPC
and serialization format.  All currently implemented collectors accept
data as binary thrift objects encapsulating individual spans. See 
[the zipkin thrift idl definitions](https://github.com/twitter/zipkin/tree/master/zipkin-thrift/src/main/resources/thrift)
for more information.

## Zipkin Scribe Collector
The primary mechanism for getting data into zipkin is using the scribe
collector.  [Scribe](http://github.com/facebook/scribe) is a
store-and-forward logging aggregator developed by facebook.  Scribe
also uses thrift as a binary encoding mechanism, which means that
zipkin scribe messages are doubly wrapped.  The scribe-compatible
thrift structure is just two strings: category and message.  The
message data is then a base64 encoded binary thrift structure
representing an individual span.  

There are a few client-side implementations of tracing that log to the
zipkin scribe collector.  The reference implementation is Twitter's
[finagle library](https://github.com/twitter/finagle/tree/master/finagle-zipkin)
in Scala.  Finagle also provides a [Ruby](https://github.com/twitter/finagle/blob/master/finagle-thrift/src/main/ruby/lib/finagle-thrift/tracer.rb)
implementation of a zipkin tracer.  [Rackspace's Tryfer](https://github.com/racker/tryfer) 
is a good example of a scribe tracer written in python.

If you are looking to add zipkin support to your favorite language,
writing a scribe/thrift implementation is a good place to start.  The
only downside is that it requires you to run the scribe daemon on all
systems actively tracing, but for testing you can point the scribe
code directly at the zipkin collector.

## Non-Scribe Collectors
The zipkin-kafka collector was written in a week as an experiment, but
it works quite well.  For organizations looking to use zipkin but
prefer another store-and-forward technology, writing a zipkin
collector for it should be quite straightforward.

## Span Convention
On top of the actual mechanism of logging spans to zipkin, there is a
preferred format of the spans being logged that will result in optimal
display of the data.  Here are some good rules to follow:

* The topmost span in a trace has its span id equal to trace id and
  parent span id is 0

* Annotate "cr","cs","sr","ss" for every leg of a trace.  For
  extremely protocol-sensitive operations such as memcache requests,
  it is acceptable to only annotate the client-side send/recieve pair.

* Include valid hosts in every annotation, along with service-specific
  service names.  The host in all cs,cr,ss,sr annotations are always
  the local host.  Service-names in cs/cr annotations refers to the
  remote service, in case the remote service does not annotate.

* There are two additional binary annotations that are helpful: "ca" and
  "sa".  These refer to the client-address and server-address
  respectively, and they are always boolean types with the value of
  true.  The host in these annotations refers to the host/port of the
  client or server calling the current span's service.  IE: if the
  server is annotating a call, ca is the remote host and sa is the
  local host.  If a client is annotating a call, ca is the local host.

* There are a number of other common annotations that have become a
  loose convention.  Here are some annotations that are common:

  * http.uri - The current uri sent to an http server
  * http.responsecode - The response code the http server returned

## TraceId Propagation
The final thing to consider when implementing zipkin on the
client-side is a mechanism to propagate trace id to downstream
services.  In order to properly reassamble traces, each service needs
to submit spans with trace ids and parent span ids that match up and
form an unbroken tree.  This is dependent on the client RPC protocol
being used, and currently there are two implementations for this.

### HTTP
All HTTP implementations of zipkin use the following X-Headers to
propagate the trace information: X-B3-SpanId, X-B3-TraceId,
X-B3-ParentSpanId, X-B3-Flags, X-B3-Sampled.  The id headers are
hex-encoded trace ids as used by zipkin elsewhere.  X-B3-Sampled is
either "true" or "false" if the current trace is being actively
sampled.  X-B3-Flags contains the debug flag, and is 0 if debugging
is off, and 1 if debugging (which forces sampling by the collector) is
enabled.

### Thrift
The finagle library also implements a mechanism for passing tracing
information over thrift.  This is done by calling a special method
name, __can__finagle__trace__v3__ when a connection is established to
a thrift service.  If the service responds to this method, then the
original rpc is wrapped with additional data as defined in [this
thrift file](https://github.com/twitter/finagle/blob/master/finagle-thrift/src/main/thrift/tracing.thrift)

## Future
This is a description of the current behavior of zipkin's collector
and clients.  However, zipkin is a very modular piece of software and
it is quite likely that new pathways for span collection will be
implemented in the future, as well as new encapsulation mechanisms for
span data.