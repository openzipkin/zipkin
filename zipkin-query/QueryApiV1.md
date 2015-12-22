FORMAT: 1A
HOST: http://localhost:9411/api/v1

# Zipkin HTTP Api v1
Zipkin's Query api is rooted at `api/v1`, on a host that by default listens on port 9411.
It primarily serves `zipkin-web`, although it includes a POST endpoint that can receive spans.

## Known Implementations
+ [Scala Server](https://github.com/openzipkin/zipkin/blob/master/zipkin-query/src/main/scala/com/twitter/zipkin/query/ZipkinQueryController.scala)
+ [Java Server](https://github.com/openzipkin/zipkin-java/blob/master/zipkin-java-server/src/main/java/io/zipkin/server/ZipkinQueryApiV1.java)
+ [Ruby Client](https://github.com/openzipkin/zipkin-tracer/blob/master/lib/zipkin-tracer/zipkin_json_tracer.rb)
+ [Go Encoder](https://github.com/adrianco/spigo/blob/master/flow/flow.go)
+ [Java Codec](https://github.com/openzipkin/zipkin-java/blob/master/zipkin-java-core/src/main/java/io/zipkin/Codec.java)
+ [Scala Codec](https://github.com/openzipkin/zipkin/blob/master/zipkin-common/src/main/scala/com/twitter/zipkin/json/ZipkinJson.scala)

# Service Names [/services]
Service names are classifiers of a source or destination in lowercase.

## List all Service Names [GET]
Returns a list of all service names associated with annotations.

+ Response 200 (application/json)
    + (array[string])
    
# Span Names [/spans/{?serviceName}]
Span names are in lowercase, rpc method for example. Conventionally, when the span name isn't known, name = "unknown".

+ Parameters
    + serviceName: zipkin-web (required) - service that logged an annotation in the span.

## List all Span Names [GET]
Returns a list of all span names which contain an annotation logged by the indicated service.

+ Response 200 (application/json)
    + (array[string])
