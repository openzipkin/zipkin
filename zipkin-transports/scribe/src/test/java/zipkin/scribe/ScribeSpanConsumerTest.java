/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.scribe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Annotation;
import zipkin.AsyncSpanConsumer;
import zipkin.BinaryAnnotation;
import zipkin.BinaryAnnotation.Type;
import zipkin.Codec;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.internal.Lazy;
import zipkin.scribe.Scribe.LogEntry;

import static com.google.common.base.Charsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.isA;

public class ScribeSpanConsumerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  List<Span> consumed = new ArrayList<>();
  AsyncSpanConsumer consumer = (input, callback) -> {
    callback.onSuccess(null);
    input.forEach(consumed::add);
  };

  Span span = new Span.Builder().traceId(1L).id(1L).name("foo").build();
  String encodedSpan = new String(Base64.getEncoder().encode(Codec.THRIFT.writeSpan(span)), UTF_8);

  @Test
  public void entriesWithSpansAreConsumed() throws Exception {
    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    LogEntry entry = new LogEntry();
    entry.category = "zipkin";
    entry.message = encodedSpan;

    assertThat(scribe.log(asList(entry)).get())
        .isEqualTo(Scribe.ResultCode.OK);

    assertThat(consumed)
        .containsExactly(span);
  }

  @Test
  public void entriesWithoutSpansAreSkipped() throws Exception {
    AsyncSpanConsumer consumer = (input, callback) -> {
      throw new AssertionError(); // as we shouldn't get here.
    };

    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    LogEntry entry = new LogEntry();
    entry.category = "notzipkin";
    entry.message = "hello world";

    scribe.log(asList(entry)).get();
  }

  @Test
  public void malformedDataSetsFutureException() throws Exception {
    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    LogEntry entry = new LogEntry();
    entry.category = "zipkin";
    entry.message = "notbase64";

    thrown.expect(ExecutionException.class); // from dereferenced future
    thrown.expectCause(isA(IllegalArgumentException.class));

    scribe.log(asList(entry)).get();
  }

  @Test
  public void consumerExceptionBeforeCallbackSetsFutureException() throws Exception {
    consumer = (input, callback) -> {
      throw new NullPointerException();
    };

    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    LogEntry entry = new LogEntry();
    entry.category = "zipkin";
    entry.message = encodedSpan;

    thrown.expect(ExecutionException.class); // from dereferenced future
    thrown.expectCause(isA(NullPointerException.class));

    scribe.log(asList(entry)).get();
  }

  @Test
  public void callbackExceptionSetsFutureException() throws Exception {
    consumer = (input, callback) -> {
      callback.onError(new NullPointerException());
    };

    ScribeSpanConsumer scribe = newScribeSpanConsumer("zipkin", consumer);

    LogEntry entry = new LogEntry();
    entry.category = "zipkin";
    entry.message = encodedSpan;

    thrown.expect(ExecutionException.class); // from dereferenced future
    thrown.expectCause(isA(NullPointerException.class));

    scribe.log(asList(entry)).get();
  }

  /** Finagle's zipkin tracer breaks on a column width with a trailing newline */
  @Test
  public void decodesSpanGeneratedByFinagle() throws Exception {
    LogEntry entry = new LogEntry();
    entry.category = "zipkin";
    entry.message =
        "CgABq/sBMnzE048LAAMAAAAOZ2V0VHJhY2VzQnlJZHMKAATN0p+4EGfTdAoABav7ATJ8xNOPDwAGDAAAAAQKAAEABR/wq+2DeAsAAgAAAAJzcgwAAwgAAX8AAAEGAAIkwwsAAwAAAAx6aXBraW4tcXVlcnkAAAoAAQAFH/Cr7zj4CwACAAAIAGFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh\n"
            + "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhDAADCAABfwAAAQYAAiTDCwADAAAADHppcGtpbi1xdWVyeQAACgABAAUf8KwLPyILAAIAAABOR2MoOSwwLlBTU2NhdmVuZ2UsMjAxNS0wOS0xNyAxMjozNzowMiArMDAwMCwzMDQubWlsbGlzZWNvbmRzKzc2Mi5taWNyb3NlY29uZHMpDAADCAABfwAAAQYAAiTDCwADAAAADHppcGtpbi1xdWVyeQAIAAQABKZ6AAoAAQAFH/CsDLfACwACAAAAAnNzDAADCAABfwAAAQYAAiTDCwADAAAADHppcGtpbi1xdWVyeQAADwAIDAAAAAULAAEAAAATc3J2L2ZpbmFnbGUudmVyc2lvbgsAAgAAAAY2LjI4LjAIAAMAAAAGDAAECAABfwAAAQYAAgAACwADAAAADHppcGtpbi1xdWVyeQAACwABAAAAD3Nydi9tdXgvZW5hYmxlZAsAAgAAAAEBCAADAAAAAAwABAgAAX8AAAEGAAIAAAsAAwAAAAx6aXBraW4tcXVlcnkAAAsAAQAAAAJzYQsAAgAAAAEBCAADAAAAAAwABAgAAX8AAAEGAAIkwwsAAwAAAAx6aXBraW4tcXVlcnkAAAsAAQAAAAJjYQsAAgAAAAEBCAADAAAAAAwABAgAAX8AAAEGAAL5YAsAAwAAAAx6aXBraW4tcXVlcnkAAAsAAQAAAAZudW1JZHMLAAIAAAAEAAAAAQgAAwAAAAMMAAQIAAF/AAABBgACJMMLAAMAAAAMemlwa2luLXF1ZXJ5AAACAAkAAA==\n";

    newScribeSpanConsumer(entry.category, consumer).log(asList(entry)).get();

    char[] as = new char[2048];
    Arrays.fill(as, 'a');
    String reallyLongAnnotation = new String(as);

    Endpoint zipkinQuery = Endpoint.create("zipkin-query", (127 << 24) | 1, 9411);
    Endpoint zipkinQuery0 = Endpoint.create("zipkin-query", (127 << 24) | 1);

    assertThat(consumed).containsExactly(
        new Span.Builder()
            .traceId(-6054243957716233329L)
            .name("getTracesByIds")
            .id(-3615651937927048332L)
            .parentId(-6054243957716233329L)
            .addAnnotation(Annotation.create(1442493420635000L, Constants.SERVER_RECV, zipkinQuery))
            .addAnnotation(Annotation.create(1442493420747000L, reallyLongAnnotation, zipkinQuery))
            .addAnnotation(Annotation.create(1442493422583586L,
                "Gc(9,0.PSScavenge,2015-09-17 12:37:02 +0000,304.milliseconds+762.microseconds)",
                zipkinQuery))
            .addAnnotation(Annotation.create(1442493422680000L, Constants.SERVER_SEND, zipkinQuery))
            .addBinaryAnnotation(
                BinaryAnnotation.create("srv/finagle.version", "6.28.0", zipkinQuery0))
            .addBinaryAnnotation(binaryAnnotation("srv/mux/enabled", true, zipkinQuery0))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, zipkinQuery))
            .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR,
                Endpoint.create("zipkin-query", (127 << 24) | 1, 63840)))
            .addBinaryAnnotation(binaryAnnotation("numIds", 1, zipkinQuery))
            .debug(false)
            .build());
  }

  static BinaryAnnotation binaryAnnotation(String key, boolean value, Endpoint endpoint) {
    return BinaryAnnotation.create(key, value ? new byte[] {1} : new byte[] {0}, Type.BOOL,
        endpoint);
  }

  static BinaryAnnotation binaryAnnotation(String key, int value, Endpoint endpoint) {
    byte[] bytes = new byte[] {
        (byte) (value >>> 24),
        (byte) (value >>> 16),
        (byte) (value >>> 8),
        (byte) value};
    return BinaryAnnotation.create(key, bytes, Type.I32, endpoint);
  }

  ScribeSpanConsumer newScribeSpanConsumer(String category, AsyncSpanConsumer consumer) {
    return new ScribeSpanConsumer(category, Lazy.of(consumer));
  }
}
