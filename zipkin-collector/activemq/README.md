# collector-activemq

## ActiveMQCollector
This collector consumes an ActiveMQ 5.x queue for messages that contain a list of spans. Underneath
this uses the ActiveMQ 5.x JMS client, which has two notable dependencies `slf4j-api` and `hawtbuf`.

The message's binary data includes a list of spans. Supported encodings
are the same as the http [POST /spans](https://zipkin.io/zipkin-api/#/paths/%252Fspans) body.

### Json
The message's binary data is a list of spans in json. The first character must be '[' (decimal 91).

`Codec.JSON.writeSpans(spans)` performs the correct json encoding.

Here's an example, sending a list of a single span to the zipkin queue:

```bash
$ curl -u admin:admin -X POST -s localhost:8161/api/message/zipkin?type=queue \
    -H "Content-Type: application/json" \
    -d '[{"traceId":"1","name":"bang","id":"2","timestamp":1470150004071068,"duration":1,"localEndpoint":{"serviceName":"flintstones"},"tags":{"lc":"bamm-bamm"}}]'
```
