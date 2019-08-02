/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.collector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.StorageComponent;

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

public class CollectorTest {
  InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  Callback<Void> callback = mock(Callback.class);
  CollectorMetrics metrics = mock(CollectorMetrics.class);
  Collector collector;
  List<String> messages = new ArrayList<>();

  Logger logger = new Logger("", null) {
    {
      setLevel(Level.ALL);
    }

    @Override public void log(Level level, String msg, Throwable thrown) {
      assertThat(level).isEqualTo(Level.FINE);
      messages.add(unprefixIdString(msg));
    }
  };

  @Before
  public void setup() {
    collector = spy(new Collector.Builder(logger).metrics(metrics).storage(storage).build());
    when(collector.idString(CLIENT_SPAN)).thenReturn("1"); // to make expectations easier to read
  }

  @After
  public void after() {
    verifyNoMoreInteractions(metrics, callback);
  }

  @Test
  public void unsampledSpansArentStored() {
    collector = new Collector.Builder(logger)
      .sampler(CollectorSampler.create(0.0f))
      .metrics(metrics)
      .storage(storage)
      .build();

    collector.accept(TRACE, callback);

    verify(callback).onSuccess(null);
    assertThat(messages).isEmpty();
    verify(metrics).incrementSpans(4);
    verify(metrics).incrementSpansDropped(4);
    assertThat(storage.getTraces()).isEmpty();
  }

  @Test
  public void errorDetectingFormat() {
    collector.acceptSpans(new byte[] {'f', 'o', 'o'}, callback);

    verify(callback).onError(any(RuntimeException.class));
    verify(metrics).incrementMessagesDropped();
  }

  @Test
  public void acceptSpans_jsonV2() {
    byte[] bytes = SpanBytesEncoder.JSON_V2.encodeList(TRACE);
    collector.acceptSpans(bytes, callback);

    verify(collector).acceptSpans(bytes, SpanBytesDecoder.JSON_V2, callback);

    verify(callback).onSuccess(null);
    assertThat(messages).isEmpty();
    verify(metrics).incrementSpans(4);
    assertThat(storage.getTraces()).containsOnly(TRACE);
  }

  @Test
  public void acceptSpans_decodingError() {
    byte[] bytes = "[\"='".getBytes(UTF_8); // screwed up json
    collector.acceptSpans(bytes, SpanBytesDecoder.JSON_V2, callback);

    verify(callback).onError(any(IllegalArgumentException.class));
    assertThat(messages).containsOnly("Malformed reading List<Span> from json");
    verify(metrics).incrementMessagesDropped();
  }

  @Test
  public void accept_storageError() {
    StorageComponent storage = mock(StorageComponent.class);
    RuntimeException error = new RuntimeException("storage disabled");
    when(storage.spanConsumer()).thenThrow(error);
    collector = new Collector.Builder(logger)
      .metrics(metrics)
      .storage(storage)
      .build();

    collector.accept(TRACE, callback);

    verify(callback).onSuccess(null); // error is async
    assertThat(messages)
      .containsOnly("Cannot store spans [1, 2, 2, ...] due to RuntimeException(storage disabled)");
    verify(metrics).incrementSpans(4);
    verify(metrics).incrementSpansDropped(4);
  }

  @Test
  public void acceptSpans_emptyMessageOk() {
    byte[] bytes = new byte[] {'[', ']'};
    collector.acceptSpans(bytes, callback);

    verify(collector).acceptSpans(bytes, SpanBytesDecoder.JSON_V1, callback);

    verify(callback).onSuccess(null);
    assertThat(messages).isEmpty();
    assertThat(storage.getTraces()).isEmpty();
  }

  @Test
  public void storeSpansCallback_toStringIncludesSpanIds() {
    Span span2 = CLIENT_SPAN.toBuilder().id("3").build();
    when(collector.idString(span2)).thenReturn("3");

    assertThat(collector.new StoreSpans(asList(CLIENT_SPAN, span2)))
      .hasToString("StoreSpans([1, 3])");
  }

  @Test
  public void storeSpansCallback_toStringIncludesSpanIds_noMoreThan3() {
    assertThat(unprefixIdString(collector.new StoreSpans(TRACE).toString()))
      .hasToString("StoreSpans([1, 1, 2, ...])");
  }

  @Test
  public void storeSpansCallback_onErrorWithNullMessage() {
    RuntimeException error = new RuntimeException();

    Callback<Void> callback = collector.new StoreSpans(TRACE);
    callback.onError(error);

    assertThat(messages)
      .containsOnly("Cannot store spans [1, 1, 2, ...] due to RuntimeException()");
    verify(metrics).incrementSpansDropped(4);
  }

  @Test
  public void storeSpansCallback_onErrorWithMessage() {
    IllegalArgumentException error = new IllegalArgumentException("no beer");
    Callback<Void> callback = collector.new StoreSpans(TRACE);
    callback.onError(error);

    assertThat(messages)
      .containsOnly("Cannot store spans [1, 1, 2, ...] due to IllegalArgumentException(no beer)");
    verify(metrics).incrementSpansDropped(4);
  }

  @Test
  public void errorAcceptingSpans_onErrorRejectedExecution() {
    RuntimeException error = new RejectedExecutionException("slow down");
    collector.handleStorageError(TRACE, error, callback);

    verify(callback).onError(error);
    assertThat(messages)
      .containsOnly(
        "Cannot store spans [1, 1, 2, ...] due to RejectedExecutionException(slow down)");
    verify(metrics).incrementSpansDropped(4);
  }

  public void handleStorageError_onErrorWithNullMessage() {
    RuntimeException error = new RuntimeException();
    collector.handleStorageError(TRACE, error, callback);

    verify(callback).onError(error);
    assertThat(messages)
      .containsOnly("Cannot store spans [1, 1, 2, ...] due to RuntimeException()");
    verify(metrics).incrementSpansDropped(4);
  }

  @Test
  public void handleStorageError_onErrorWithMessage() {
    RuntimeException error = new IllegalArgumentException("no beer");
    collector.handleStorageError(TRACE, error, callback);

    verify(callback).onError(error);
    assertThat(messages)
      .containsOnly("Cannot store spans [1, 1, 2, ...] due to IllegalArgumentException(no beer)");
    verify(metrics).incrementSpansDropped(4);
  }

  @Test
  public void handleDecodeError_onErrorWithNullMessage() {
    RuntimeException error = new RuntimeException();
    collector.handleDecodeError(error, callback);

    verify(callback).onError(error);
    assertThat(messages).containsOnly("Cannot decode spans due to RuntimeException()");
    verify(metrics).incrementMessagesDropped();
  }

  @Test
  public void handleDecodeError_onErrorWithMessage() {
    IllegalArgumentException error = new IllegalArgumentException("no beer");
    collector.handleDecodeError(error, callback);

    verify(callback).onError(error);
    assertThat(messages)
      .containsOnly("Cannot decode spans due to IllegalArgumentException(no beer)");
    verify(metrics).incrementMessagesDropped();
  }

  @Test
  public void handleDecodeError_doesntWrapMessageOnMalformedException() {
    IllegalArgumentException error = new IllegalArgumentException("Malformed reading spans");
    collector.handleDecodeError(error, callback);

    verify(callback).onError(error);
    assertThat(messages).containsOnly("Malformed reading spans");
    verify(metrics).incrementMessagesDropped();
  }

  @Test
  public void handleDecodeError_doesntWrapMessageOnTruncatedException() {
    IllegalArgumentException error = new IllegalArgumentException("Truncated reading spans");
    collector.handleDecodeError(error, callback);

    verify(callback).onError(error);
    assertThat(messages).containsOnly("Truncated reading spans");
    verify(metrics).incrementMessagesDropped();
  }

  String unprefixIdString(String msg) {
    return msg.replaceAll("7180c278b62e8f6a216a2aea45d08fc9/000000000000000", "");
  }
}
