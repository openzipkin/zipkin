/*
 * Copyright 2015-2018 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.collector.scribe;

import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;

import static com.google.common.base.Charsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.isA;

public class ScribeSpanConsumerTest {

  @Rule public ExpectedException thrown = ExpectedException.none();
  // scope to scribe as we aren't creating the consumer with the builder.
  InMemoryCollectorMetrics scribeMetrics = new InMemoryCollectorMetrics().forTransport("scribe");

  InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  SpanConsumer consumer = storage.spanConsumer();

  static String reallyLongAnnotation;

  static {
    char[] as = new char[2048];
    Arrays.fill(as, 'a');
    reallyLongAnnotation = new String(as);
  }

  Endpoint zipkinQuery =
      Endpoint.newBuilder().serviceName("zipkin-query").ip("127.0.0.1").port(9411).build();
  Endpoint zipkinQuery0 = zipkinQuery.toBuilder().port(null).build();

  V1Span v1 =
      V1Span.newBuilder()
          .traceId(-6054243957716233329L)
          .name("getTracesByIds")
          .id(-3615651937927048332L)
          .parentId(-6054243957716233329L)
          .addAnnotation(1442493420635000L, "sr", zipkinQuery)
          .addAnnotation(1442493420747000L, reallyLongAnnotation, zipkinQuery)
          .addAnnotation(
              1442493422583586L,
              "Gc(9,0.PSScavenge,2015-09-17 12:37:02 +0000,304.milliseconds+762.microseconds)",
              zipkinQuery)
          .addAnnotation(1442493422680000L, "ss", zipkinQuery)
          .addBinaryAnnotation("srv/finagle.version", "6.28.0", zipkinQuery0)
          .addBinaryAnnotation("sa", zipkinQuery)
          .addBinaryAnnotation("ca", zipkinQuery.toBuilder().port(63840).build())
          .debug(false)
          .build();

  Span v2 = V1SpanConverter.create().convert(v1).get(0);
  byte[] bytes = SpanBytesEncoder.THRIFT.encode(v2);
  String encodedSpan = new String(Base64.getEncoder().encode(bytes), UTF_8);

  @Test
  public void entriesWithSpansAreConsumed() throws Exception {
    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    Scribe.LogEntry entry = new Scribe.LogEntry();
    entry.category = "zipkin";
    entry.message = encodedSpan;

    assertThat(scribe.log(asList(entry)).get()).isEqualTo(Scribe.ResultCode.OK);

    assertThat(storage.getTraces()).containsExactly(asList(v2));

    assertThat(scribeMetrics.messages()).isEqualTo(1);
    assertThat(scribeMetrics.bytes()).isEqualTo(bytes.length);
    assertThat(scribeMetrics.spans()).isEqualTo(1);
  }

  @Test
  public void entriesWithoutSpansAreSkipped() throws Exception {
    SpanConsumer consumer =
        (callback) -> {
          throw new AssertionError(); // as we shouldn't get here.
        };

    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    Scribe.LogEntry entry = new Scribe.LogEntry();
    entry.category = "notzipkin";
    entry.message = "hello world";

    scribe.log(asList(entry)).get();

    assertThat(scribeMetrics.bytes()).isZero();
    assertThat(scribeMetrics.spans()).isZero();
  }

  @Test
  public void malformedDataIsDropped() throws Exception {
    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    Scribe.LogEntry entry = new Scribe.LogEntry();
    entry.category = "zipkin";
    entry.message = "notbase64";

    thrown.expect(ExecutionException.class); // from dereferenced future
    thrown.expectCause(isA(IllegalArgumentException.class));

    scribe.log(asList(entry)).get();
  }

  @Test
  public void consumerExceptionBeforeCallbackSetsFutureException() throws Exception {
    consumer =
        (input) -> {
          throw new NullPointerException();
        };

    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    Scribe.LogEntry entry = new Scribe.LogEntry();
    entry.category = "zipkin";
    entry.message = encodedSpan;

    thrown.expect(ExecutionException.class); // from dereferenced future
    thrown.expectMessage("Cannot store spans [abfb01327cc4d38f/cdd29fb81067d374]");

    scribe.log(asList(entry)).get();
  }

  /**
   * Callbacks are performed asynchronously. If they throw, it hints that we are chaining futures
   * when we shouldn't
   */
  @Test
  public void callbackExceptionDoesntThrow() throws Exception {
    consumer =
        (input) ->
            new Call.Base<Void>() {

              @Override
              protected Void doExecute() {
                throw new AssertionError();
              }

              @Override
              protected void doEnqueue(Callback callback) {
                callback.onError(new NullPointerException());
              }

              @Override
              public Call clone() {
                throw new AssertionError();
              }
            };

    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    Scribe.LogEntry entry = new Scribe.LogEntry();
    entry.category = "zipkin";
    entry.message = encodedSpan;

    scribe.log(asList(entry)).get();

    assertThat(scribeMetrics.spansDropped()).isEqualTo(1);
  }

  /** Finagle's zipkin tracer breaks on a column width with a trailing newline */
  @Test
  public void decodesSpanGeneratedByFinagle() throws Exception {
    Scribe.LogEntry entry = new Scribe.LogEntry();
    entry.category = "zipkin";
    entry.message =
        "CgABq/sBMnzE048LAAMAAAAOZ2V0VHJhY2VzQnlJZHMKAATN0p+4EGfTdAoABav7ATJ8xNOPDwAGDAAAAAQKAAEABR/wq+2DeAsAAgAAAAJzcgwAAwgAAX8AAAEGAAIkwwsAAwAAAAx6aXBraW4tcXVlcnkAAAoAAQAFH/Cr7zj4CwACAAAIAGFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh\n"
            + "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhDAADCAABfwAAAQYAAiTDCwADAAAADHppcGtpbi1xdWVyeQAACgABAAUf8KwLPyILAAIAAABOR2MoOSwwLlBTU2NhdmVuZ2UsMjAxNS0wOS0xNyAxMjozNzowMiArMDAwMCwzMDQubWlsbGlzZWNvbmRzKzc2Mi5taWNyb3NlY29uZHMpDAADCAABfwAAAQYAAiTDCwADAAAADHppcGtpbi1xdWVyeQAIAAQABKZ6AAoAAQAFH/CsDLfACwACAAAAAnNzDAADCAABfwAAAQYAAiTDCwADAAAADHppcGtpbi1xdWVyeQAADwAIDAAAAAULAAEAAAATc3J2L2ZpbmFnbGUudmVyc2lvbgsAAgAAAAY2LjI4LjAIAAMAAAAGDAAECAABfwAAAQYAAgAACwADAAAADHppcGtpbi1xdWVyeQAACwABAAAAD3Nydi9tdXgvZW5hYmxlZAsAAgAAAAEBCAADAAAAAAwABAgAAX8AAAEGAAIAAAsAAwAAAAx6aXBraW4tcXVlcnkAAAsAAQAAAAJzYQsAAgAAAAEBCAADAAAAAAwABAgAAX8AAAEGAAIkwwsAAwAAAAx6aXBraW4tcXVlcnkAAAsAAQAAAAJjYQsAAgAAAAEBCAADAAAAAAwABAgAAX8AAAEGAAL5YAsAAwAAAAx6aXBraW4tcXVlcnkAAAsAAQAAAAZudW1JZHMLAAIAAAAEAAAAAQgAAwAAAAMMAAQIAAF/AAABBgACJMMLAAMAAAAMemlwa2luLXF1ZXJ5AAACAAkAAA==\n";

    newScribeSpanConsumer(entry.category, consumer).log(asList(entry)).get();

    assertThat(storage.getTraces()).containsExactly(asList(v2));
  }

  ScribeSpanConsumer newScribeSpanConsumer(String category, SpanConsumer consumer) {
    return new ScribeSpanConsumer(
        ScribeCollector.newBuilder()
            .category(category)
            .metrics(scribeMetrics)
            .storage(
                new StorageComponent() {
                  @Override
                  public SpanStore spanStore() {
                    throw new AssertionError();
                  }

                  @Override
                  public SpanConsumer spanConsumer() {
                    return consumer;
                  }

                  @Override
                  public CheckResult check() {
                    return CheckResult.OK;
                  }

                  @Override
                  public void close() {
                    throw new AssertionError();
                  }
                }));
  }
}
