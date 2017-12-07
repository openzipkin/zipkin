# collector-rabbitmq

## RabbitMQCollector
This collector consumes a RabbitMQ queue for messages that contain a list of spans.
Its only dependencies besides Zipkin core are the `slf4j-api` and the [RabbitMQ Java Client](https://github.com/rabbitmq/rabbitmq-java-client).

### Configuration

The following configuration can be set for the RabbitMQ Collector.

Property | Environment Variable | Description
--- | --- | ---
`zipkin.collector.rabbitmq.addresses` | `RABBIT_ADDRESSES` | Comma-separated list of RabbitMQ addresses, ex. `localhost:5672,localhost:5673`
`zipkin.collector.rabbitmq.concurrency` | `RABBIT_CONCURRENCY` | Number of concurrent consumers. Defaults to `1`
`zipkin.collector.rabbitmq.connection-timeout` | `RABBIT_CONNECTION_TIMEOUT` | Milliseconds to wait establishing a connection. Defaults to `60000` (1 minute)
`zipkin.collector.rabbitmq.password` | `RABBIT_PASSWORD`| Password to use when connecting to RabbitMQ. Defaults to `guest`
`zipkin.collector.rabbitmq.queue` | `RABBIT_QUEUE` | Queue from which to collect span messages. Defaults to `zipkin`
`zipkin.collector.rabbitmq.username` | `RABBIT_USER` | Username to use when connecting to RabbitMQ. Defaults to `guest`
`zipkin.collector.rabbitmq.virtual-host` | `RABBIT_VIRTUAL_HOST` | RabbitMQ virtual host to use. Defaults to `/`
`zipkin.collector.rabbitmq.use-ssl` | `RABBIT_USE_SSL` | Set to `true` to use SSL when connecting to RabbitMQ

### Caveats

The configured queue will be idempotently declared as a durable queue.

This collector uses one connection to RabbitMQ, with the configured `concurrency` number of threads
each using one channel to consume messages.

Consumption is done with `autoAck` on, so messages that fail to process successfully are not retried.

## Encoding spans into RabbitMQ messages
The message's body should be the bytes of an encoded list of spans.

### JSON
A list of Spans in JSON. The first character must be '[' (decimal 91).

`SpanBytesEncoder.JSON_V2.encodeList(spans)` performs the correct JSON encoding.

## Local testing

The following assumes you are running an instance of RabbitMQ locally on the default port (5672).
You can download and install RabbitMQ following [instructions available here](https://www.rabbitmq.com/download.html).
With the [RabbitMQ Management CLI](https://www.rabbitmq.com/management-cli.html) you can easily publish
one-off spans to RabbitMQ to be collected by this collector.

1. Start RabbitMQ server
2. Start Zipkin server
```bash
$ RABBIT_ADDRESSES=localhost java -jar zipkin.jar
```
3. Save an array of spans to a file like `sample-spans.json`
```json
[{"traceId":"9032b04972e475c5","id":"9032b04972e475c5","kind":"SERVER","name":"get","timestamp":1505990621526000,"duration":612898,"localEndpoint":{"serviceName":"brave-webmvc-example","ipv4":"192.168.1.113"},"remoteEndpoint":{"serviceName":"","ipv4":"127.0.0.1","port":60149},"tags":{"error":"500 Internal Server Error","http.path":"/a"}}]
```
4. Publish them using the CLI 
```bash
$ rabbitmqadmin publish exchange=amq.default routing_key=zipkin < sample-spans.json
```
