Span Receivers
==============

A `SpanReceiver` is responsible for collecting spans from services, converting
them to a Zipkin common Span, then passing them to the storage layer. This
approach provides modularity allowing for receivers that accept any sort of data
from any sort of producer. Zipkin comes with a receiver for Scribe and one for
Kafka.

Scribe Receiver
---------------

Scribe was the logging framework in use at Twitter to transport trace data when
Zipkin was created. That's why there is a scribe span receiver in the project.

For small architectures tracers can be setup to send directly to the Zipkin
collectors. The ScribeSpanReceiver expects a scribe log entry with a Base64
encoded, binary serialized thrift Span using the "zipkin" category (this
category is configurable via command line flag). Finagle-zipkin does this
automatically.
