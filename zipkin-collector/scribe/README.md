# collector-scribe

## ScribeCollector
This collector accepts Scribe logs in a specified category. Each log
entry is expected to contain a single span, which is TBinaryProtocol
big-endian, then base64 encoded. These spans are then pushed to storage.

`zipkin.collector.scribe.ScribeCollector.Builder` includes defaults that will
listen on port 9410, accept log entries in the category "zipkin"

## Encoding
The scribe message is a TBinaryProtocol big-endian, then Base64 span.
Base64 Basic and MIME schemes are supported.

Here's what it looks like in pseudocode
```
serialized = writeTBinaryProtocol(span)
encoded = base64(serialized)

scribe.log(category = "zipkin", message = encoded)
```
