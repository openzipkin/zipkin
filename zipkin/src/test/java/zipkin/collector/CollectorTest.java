/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.collector;

import org.junit.Before;
import org.junit.Test;
import zipkin.Span;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.storage.Callback;
import zipkin.storage.StorageComponent;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static zipkin.TestObjects.LOTS_OF_SPANS;
import static zipkin.storage.Callback.NOOP;

public class CollectorTest {
  StorageComponent storage = mock(StorageComponent.class);
  Collector collector;
  Span span1 = ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[0]);
  Span span2 = ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[1]);

  @Before public void setup() throws Exception {
    collector = spy(Collector.builder(Collector.class)
      .storage(storage).build());
    when(collector.idString(span1)).thenReturn("1"); // to make expectations easier to read
    doAnswer(invocation -> null).when(collector).warn(any(String.class), any(Throwable.class));
  }

  @Test
  public void acceptSpansCallback_toStringIncludesSpanIds() {
    when(collector.idString(span2)).thenReturn("2");

    assertThat(collector.acceptSpansCallback(asList(span1, span2)))
      .hasToString("AcceptSpans([1, 2])");
  }

  @Test
  public void acceptSpansCallback_onErrorWithNullMessage() {
    Callback<Void> callback = collector.acceptSpansCallback(asList(span1));

    RuntimeException exception = new RuntimeException();
    callback.onError(exception);

    verify(collector).warn("Cannot store spans [1] due to RuntimeException()", exception);
  }

  @Test
  public void acceptSpansCallback_onErrorWithMessage() {
    Callback<Void> callback = collector.acceptSpansCallback(asList(span1));
    RuntimeException exception = new IllegalArgumentException("no beer");
    callback.onError(exception);

    verify(collector)
      .warn("Cannot store spans [1] due to IllegalArgumentException(no beer)", exception);
  }

  @Test
  public void errorAcceptingSpans_onErrorWithNullMessage() {
    String message =
      collector.errorStoringSpans(asList(span1), new RuntimeException()).getMessage();

    assertThat(message)
      .isEqualTo("Cannot store spans [1] due to RuntimeException()");
  }

  @Test
  public void errorAcceptingSpans_onErrorWithMessage() {
    RuntimeException exception = new IllegalArgumentException("no beer");
    String message = collector.errorStoringSpans(asList(span1), exception).getMessage();

    assertThat(message)
      .isEqualTo("Cannot store spans [1] due to IllegalArgumentException(no beer)");
  }

  @Test
  public void errorDecoding_onErrorWithNullMessage() {
    String message = collector.errorReading(new RuntimeException()).getMessage();

    assertThat(message)
      .isEqualTo("Cannot decode spans due to RuntimeException()");
  }

  @Test
  public void errorDecoding_onErrorWithMessage() {
    RuntimeException exception = new IllegalArgumentException("no beer");
    String message = collector.errorReading(exception).getMessage();

    assertThat(message)
      .isEqualTo("Cannot decode spans due to IllegalArgumentException(no beer)");
  }

  @Test
  public void errorDecoding_doesntWrapMalformedException() {
    RuntimeException exception = new IllegalArgumentException("Malformed reading spans");

    String message = collector.errorReading(exception).getMessage();

    assertThat(message)
      .isEqualTo("Malformed reading spans");
  }

  @Test public void unsampledSpansArentStored() {
    when(storage.asyncSpanConsumer()).thenThrow(new AssertionError());

    collector = Collector.builder(Collector.class)
      .sampler(CollectorSampler.create(0.0f))
      .storage(storage).build();

    collector.accept(asList(span1), NOOP);
  }

  @Test public void doesntCallDeprecatedSampleMethod() {
    CollectorSampler sampler = mock(CollectorSampler.class);
    when(sampler.isSampled(span1)).thenThrow(new AssertionError());

    collector = Collector.builder(Collector.class)
      .sampler(sampler)
      .storage(storage).build();

    collector.accept(asList(span1), NOOP);

    verify(sampler).isSampled(span1.traceId, span1.debug);
  }
}
