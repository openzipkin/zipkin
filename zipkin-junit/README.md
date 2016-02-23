# zipkin-junit

This contains `ZipkinRule`, a JUnit rule to spin-up a Zipkin server during tests.

For example, you can write micro-integration tests like so:

```java
@Rule
public ZipkinRule zipkin = new ZipkinRule();

// Pretend we have a traced system under test
TracedService service = new TracedService(zipkin.httpUrl(), ReportingMode.FLUSH_EVERY);

@Test
public void skipsReportingWhenNotSampled() throws IOException {
  zipkin.storeSpans(asList(rootSpan));

  // send a request to the instrumented server, telling it not to sample.
  client.addHeader("X-B3-TraceId", rootSpan.traceId)
        .addHeader("X-B3-SpanId", rootSpan.id)
        .addHeader("X-B3-Sampled", 0).get(service.httpUrl());

  // check that zipkin didn't receive any new data in that trace
  assertThat(zipkin.getTraces()).containsOnly(asList(rootSpan));
}
```
