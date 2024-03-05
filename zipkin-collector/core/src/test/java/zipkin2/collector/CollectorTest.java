/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector;

import com.github.valfirst.slf4jtest.TestLoggerFactoryExtension;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.StorageComponent;

import static com.github.valfirst.slf4jtest.TestLoggerFactory.getLoggingEvents;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.TRACE;
import static zipkin2.TestObjects.UTF_8;

@ExtendWith(TestLoggerFactoryExtension.class)
class CollectorTest {
  InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  Callback<Void> callback = mock(Callback.class);
  CollectorMetrics metrics = mock(CollectorMetrics.class);
  Collector collector;
  Logger testLogger = LoggerFactory.getLogger(CollectorTest.class);

  @BeforeEach void setup() {
    collector = spy(
      new Collector.Builder(testLogger).metrics(metrics).storage(storage).build());
    when(collector.idString(CLIENT_SPAN)).thenReturn("1"); // to make expectations easier to read
  }

  @AfterEach void after() {
    verifyNoMoreInteractions(metrics, callback);
  }

  @Test void unsampledSpansArentStored() {
    collector = new Collector.Builder(LoggerFactory.getLogger(""))
      .sampler(CollectorSampler.create(0.0f))
      .metrics(metrics)
      .storage(storage)
      .build();

    collector.accept(TRACE, callback);

    verify(callback).onSuccess(null);
    assertThat(getLoggingEvents()).isEmpty();
    verify(metrics).incrementSpans(4);
    verify(metrics).incrementSpansDropped(4);
    assertThat(storage.getTraces()).isEmpty();
  }

  @Test void errorDetectingFormat() {
    collector.acceptSpans(new byte[] {'f', 'o', 'o'}, callback);

    verify(callback).onError(any(RuntimeException.class));
    verify(metrics).incrementMessagesDropped();
  }

  @Test void acceptSpans_jsonV2() {
    byte[] bytes = SpanBytesEncoder.JSON_V2.encodeList(TRACE);
    collector.acceptSpans(bytes, callback);

    verify(collector).acceptSpans(bytes, SpanBytesDecoder.JSON_V2, callback);

    verify(callback).onSuccess(null);
    assertThat(getLoggingEvents()).isEmpty();
    verify(metrics).incrementSpans(4);
    assertThat(storage.getTraces()).containsOnly(TRACE);
  }

  @Test void acceptSpans_decodingError() {
    byte[] bytes = "[\"='".getBytes(UTF_8); // screwed up json
    collector.acceptSpans(bytes, SpanBytesDecoder.JSON_V2, callback);

    verify(callback).onError(any(IllegalArgumentException.class));
    assertDebugLogIs("Malformed reading List<Span> from json");
    verify(metrics).incrementMessagesDropped();
  }

  /** Tags in zipkin v2 model are stringly typed. */
  @Test void acceptSpans_decodingError_nonStringValue() {
    byte[] bytes = """
      {
        "traceId": "6b221d5bc9e6496c",
        "name": "get-traces",
        "id": "6b221d5bc9e6496c",
        "tags": {
          "error": true
        }
      }
      """.getBytes(UTF_8); // error tag has a bool instead of string value
    collector.acceptSpans(bytes, SpanBytesDecoder.JSON_V2, callback);

    verify(callback).onError(any(IllegalArgumentException.class));
    assertDebugLogIs("Malformed reading List<Span> from json");
    verify(metrics).incrementMessagesDropped();
  }

  @Test void accept_storageError() {
    StorageComponent storage = mock(StorageComponent.class);
    RuntimeException error = new RuntimeException("storage disabled");
    when(storage.spanConsumer()).thenThrow(error);
    collector = new Collector.Builder(LoggerFactory.getLogger(""))
      .metrics(metrics)
      .storage(storage)
      .build();

    collector.accept(TRACE, callback);

    verify(callback).onSuccess(null); // error is async
    assertDebugLogIs("Cannot store spans [1, 2, 2, ...] due to RuntimeException(storage disabled)");
    verify(metrics).incrementSpans(4);
    verify(metrics).incrementSpansDropped(4);
  }

  @Test void acceptSpans_emptyMessageOk() {
    byte[] bytes = new byte[] {'[', ']'};
    collector.acceptSpans(bytes, callback);

    verify(collector).acceptSpans(bytes, SpanBytesDecoder.JSON_V1, callback);

    verify(callback).onSuccess(null);
    assertThat(getLoggingEvents()).isEmpty();
    assertThat(storage.getTraces()).isEmpty();
  }

  @Test void storeSpansCallback_toStringIncludesSpanIds() {
    Span span2 = CLIENT_SPAN.toBuilder().id("3").build();
    when(collector.idString(span2)).thenReturn("3");

    assertThat(collector.new StoreSpans(asList(CLIENT_SPAN, span2)))
      .hasToString("StoreSpans([1, 3])");
  }

  @Test void storeSpansCallback_toStringIncludesSpanIds_noMoreThan3() {
    assertThat(unprefixIdString(collector.new StoreSpans(TRACE).toString()))
      .hasToString("StoreSpans([1, 1, 2, ...])");
  }

  @Test void storeSpansCallback_onErrorWithNullMessage() {
    RuntimeException error = new RuntimeException();

    Callback<Void> callback = collector.new StoreSpans(TRACE);
    callback.onError(error);

    assertDebugLogIs("Cannot store spans [1, 1, 2, ...] due to RuntimeException()");
    verify(metrics).incrementSpansDropped(4);
  }

  @Test void storeSpansCallback_onErrorWithMessage() {
    IllegalArgumentException error = new IllegalArgumentException("no beer");
    Callback<Void> callback = collector.new StoreSpans(TRACE);
    callback.onError(error);

    assertDebugLogIs("Cannot store spans [1, 1, 2, ...] due to IllegalArgumentException(no beer)");
    verify(metrics).incrementSpansDropped(4);
  }

  @Test void errorAcceptingSpans_onErrorRejectedExecution() {
    RuntimeException error = new RejectedExecutionException("slow down");
    collector.handleStorageError(TRACE, error, callback);

    verify(callback).onError(error);
    assertDebugLogIs(
      "Cannot store spans [1, 1, 2, ...] due to RejectedExecutionException(slow down)");
    verify(metrics).incrementSpansDropped(4);
  }

  @Test void handleStorageError_onErrorWithNullMessage() {
    RuntimeException error = new RuntimeException();
    collector.handleStorageError(TRACE, error, callback);

    verify(callback).onError(error);
    assertDebugLogIs("Cannot store spans [1, 1, 2, ...] due to RuntimeException()");
    verify(metrics).incrementSpansDropped(4);
  }

  @Test void handleStorageError_onErrorWithMessage() {
    RuntimeException error = new IllegalArgumentException("no beer");
    collector.handleStorageError(TRACE, error, callback);

    verify(callback).onError(error);
    assertDebugLogIs("Cannot store spans [1, 1, 2, ...] due to IllegalArgumentException(no beer)");
    verify(metrics).incrementSpansDropped(4);
  }

  @Test void handleDecodeError_onErrorWithNullMessage() {
    RuntimeException error = new RuntimeException();
    collector.handleDecodeError(error, callback);

    verify(callback).onError(error);
    assertDebugLogIs("Cannot decode spans due to RuntimeException()");
    verify(metrics).incrementMessagesDropped();
  }

  @Test void handleDecodeError_onErrorWithMessage() {
    IllegalArgumentException error = new IllegalArgumentException("no beer");
    collector.handleDecodeError(error, callback);

    verify(callback).onError(error);
    assertDebugLogIs("Cannot decode spans due to IllegalArgumentException(no beer)");
    verify(metrics).incrementMessagesDropped();
  }

  @Test void handleDecodeError_doesntWrapMessageOnMalformedException() {
    IllegalArgumentException error = new IllegalArgumentException("Malformed reading spans");
    collector.handleDecodeError(error, callback);

    verify(callback).onError(error);
    assertDebugLogIs("Malformed reading spans");
    verify(metrics).incrementMessagesDropped();
  }

  @Test void handleDecodeError_doesntWrapMessageOnTruncatedException() {
    IllegalArgumentException error = new IllegalArgumentException("Truncated reading spans");
    collector.handleDecodeError(error, callback);

    verify(callback).onError(error);
    assertDebugLogIs("Truncated reading spans");
    verify(metrics).incrementMessagesDropped();
  }

  private String unprefixIdString(String msg) {
    return msg.replaceAll("7180c278b62e8f6a216a2aea45d08fc9/000000000000000", "");
  }

  private void assertDebugLogIs(String message) {
    assertThat(getLoggingEvents())
      .hasSize(1)
      .filteredOn(event -> event.getLevel().equals(Level.DEBUG))
      .extracting(event -> unprefixIdString(event.getMessage()))
      .containsOnly(message);
  }
}
