# collector-activemq

## ActiveMQCollector
This collector consumes a ActiveMQ queue for messages that contain a list of spans.
Its only dependencies besides Zipkin core are the `slf4j-api` and the [ActiveMQ Java Client](https://github.com/apache/activemq).

### Configuration

The following configuration can be set for the ActiveMQ Collector.

Property | Environment Variable | Description
--- | --- | ---
`zipkin.collector.activemq.connection-timeout` | `ACTIVE_CONNECTION_TIMEOUT` | Milliseconds to wait establishing a connection. Defaults to `60000` (1 minute)
`zipkin.collector.activemq.queue` | `ACTIVE_QUEUE` | Queue from which to collect span messages. Defaults to `zipkin`

If the URI is set, the following properties will be ignored.

Property | Environment Variable | Description
--- | --- | ---
`zipkin.collector.activemq.addresses` | `ACTIVE_ADDRESSES` | Comma-separated list of ActiveMQ addresses, ex. `localhost:5672,localhost:5673`
`zipkin.collector.activemq.password` | `ACTIVE_PASSWORD`| Password to use when connecting to ActiveMQ. Defaults to `guest`
`zipkin.collector.activemq.username` | `ACTIVE_USER` | Username to use when connecting to ActiveMQ. Defaults to `guest`

### Caveats

The configured queue will be idempotently declared as a durable queue.


Consumption is done with `autoAck` on, so messages that fail to process successfully are not retried.

## Encoding spans into ActiveMQ messages
The message's body should be the bytes of an encoded list of spans.

### JSON
A list of Spans in JSON. The first character must be '[' (decimal 91).

`SpanBytesEncoder.JSON_V2.encodeList(spans)` performs the correct JSON encoding.

## Local testing

The following assumes you are running an instance of ActiveMQ locally on the default port (61616).
You can download and install ActiveMQ following [instructions available here](http://activemq.apache.org/download.html).
With the [ActiveMQ Management Admin](http://localhost:8161/admin/) you can easily publish
one-off spans to ActiveMQ to be collected by this collector.

1. Start ActiveMQ server
2. Start Zipkin server
```bash
$ ACTIVE_ADDRESSES=tcp://localhost:61616 java -jar zipkin.jar
```
3. Save an array of spans to a file like `sample-spans.json`
```json
[{"traceId":"9032b04972e475c5","id":"9032b04972e475c5","kind":"SERVER","name":"get","timestamp":1505990621526000,"duration":612898,"localEndpoint":{"serviceName":"brave-webmvc-example","ipv4":"192.168.1.113"},"remoteEndpoint":{"serviceName":"","ipv4":"127.0.0.1","port":60149},"tags":{"error":"500 Internal Server Error","http.path":"/a"}}]
```
4. Publish them using the admin 

