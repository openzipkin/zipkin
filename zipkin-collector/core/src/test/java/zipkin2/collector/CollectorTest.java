/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.collector;

import org.junit.Before;
import org.junit.Test;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.StorageComponent;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static zipkin2.TestObjects.CLIENT_SPAN;

public class CollectorTest {
  StorageComponent storage = mock(StorageComponent.class);
  Callback callback = mock(Callback.class);
  Collector collector;

  @Before
  public void setup() {
    collector = spy(Collector.newBuilder(Collector.class).storage(storage).build());
    when(collector.shouldWarn()).thenReturn(true);
    when(collector.idString(CLIENT_SPAN)).thenReturn("1"); // to make expectations easier to read
  }

  @Test
  public void unsampledSpansArentStored() {
    when(storage.spanConsumer()).thenThrow(new AssertionError());

    collector =
        Collector.newBuilder(Collector.class)
            .sampler(CollectorSampler.create(0.0f))
            .storage(storage)
            .build();

    collector.accept(asList(CLIENT_SPAN), callback);
  }

  @Test
  public void errorDetectingFormat() {
    CollectorMetrics metrics = mock(CollectorMetrics.class);

    collector = Collector.newBuilder(Collector.class).metrics(metrics).storage(storage).build();

    collector.acceptSpans(new byte[] {'f', 'o', 'o'}, callback);

    verify(metrics).incrementMessagesDropped();
  }

  @Test
  public void convertsSpan2Format() {
    byte[] bytes = SpanBytesEncoder.JSON_V2.encodeList(asList(CLIENT_SPAN));
    collector.acceptSpans(bytes, callback);

    verify(collector).acceptSpans(bytes, SpanBytesDecoder.JSON_V2, callback);
    verify(collector).accept(asList(CLIENT_SPAN), callback);
  }

  @Test
  public void acceptSpansCallback_toStringIncludesSpanIds() {
    Span span2 = CLIENT_SPAN.toBuilder().id("3").build();
    when(collector.idString(span2)).thenReturn("3");

    assertThat(collector.acceptSpansCallback(asList(CLIENT_SPAN, span2)))
        .hasToString("AcceptSpans([1, 3])");
  }

  @Test
  public void acceptSpansCallback_toStringIncludesSpanIds_noMoreThan3() {
    assertThat(
            collector.acceptSpansCallback(
                asList(CLIENT_SPAN, CLIENT_SPAN, CLIENT_SPAN, CLIENT_SPAN)))
        .hasToString("AcceptSpans([1, 1, 1, ...])");
  }

  @Test
  public void acceptSpansCallback_onErrorWithNullMessage() {
    Callback<Void> callback = collector.acceptSpansCallback(asList(CLIENT_SPAN));

    RuntimeException exception = new RuntimeException();
    callback.onError(exception);

    verify(collector).warn("Cannot store spans [1] due to RuntimeException()", exception);
  }

  @Test
  public void acceptSpansCallback_onErrorWithMessage() {
    Callback<Void> callback = collector.acceptSpansCallback(asList(CLIENT_SPAN));
    RuntimeException exception = new IllegalArgumentException("no beer");
    callback.onError(exception);

    verify(collector)
        .warn("Cannot store spans [1] due to IllegalArgumentException(no beer)", exception);
  }

  @Test
  public void errorAcceptingSpans_onErrorWithNullMessage() {
    String message =
        collector.errorStoringSpans(asList(CLIENT_SPAN), new RuntimeException()).getMessage();

    assertThat(message).isEqualTo("Cannot store spans [1] due to RuntimeException()");
  }

  @Test
  public void errorAcceptingSpans_onErrorWithMessage() {
    RuntimeException exception = new IllegalArgumentException("no beer");
    String message = collector.errorStoringSpans(asList(CLIENT_SPAN), exception).getMessage();

    assertThat(message)
        .isEqualTo("Cannot store spans [1] due to IllegalArgumentException(no beer)");
  }

  @Test
  public void errorDecoding_onErrorWithNullMessage() {
    String message = collector.errorReading(new RuntimeException()).getMessage();

    assertThat(message).isEqualTo("Cannot decode spans due to RuntimeException()");
  }

  @Test
  public void errorDecoding_onErrorWithMessage() {
    RuntimeException exception = new IllegalArgumentException("no beer");
    String message = collector.errorReading(exception).getMessage();

    assertThat(message).isEqualTo("Cannot decode spans due to IllegalArgumentException(no beer)");
  }

  @Test
  public void errorDecoding_doesntWrapMalformedException() {
    RuntimeException exception = new IllegalArgumentException("Malformed reading spans");

    String message = collector.errorReading(exception).getMessage();

    assertThat(message).isEqualTo("Malformed reading spans");
  }

  @Test
  public void errorDecoding_doesntWrapTruncatedException() {
    RuntimeException exception = new IllegalArgumentException("Truncated reading spans");

    String message = collector.errorReading(exception).getMessage();

    assertThat(message).isEqualTo("Truncated reading spans");
  }
}
