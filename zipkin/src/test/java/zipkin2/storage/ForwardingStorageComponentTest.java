/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zipkin2.CheckResult;
import zipkin2.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ForwardingStorageComponentTest {
  /**
   * This test is intentionally brittle. It should break if we add new methods on {@link
   * StorageComponent}!
   */
  @Test void declaresAllMethodsToForward() {
    assertThat(ForwardingStorageComponent.class.getDeclaredMethods())
      .extracting(Method::getName)
      .containsAll(Stream.concat(
        Stream.of(Component.class.getDeclaredMethods()).map(Method::getName),
        Stream.of(StorageComponent.class.getDeclaredMethods()).map(Method::getName)
      ).collect(Collectors.toList()));
  }

  StorageComponent delegate = mock(StorageComponent.class);
  StorageComponent forwarder = new ForwardingStorageComponent() {
    @Override protected StorageComponent delegate() {
      return delegate;
    }
  };

  @AfterEach void verifyNoExtraCalls() {
    verifyNoMoreInteractions(delegate);
  }

  @Test void delegatesToString() {
    assertThat(forwarder.toString()).isEqualTo(delegate.toString());
  }

  @Test void delegatesCheck() {
    CheckResult down = CheckResult.failed(new RuntimeException("failed"));
    when(delegate.check()).thenReturn(down);

    assertThat(forwarder.check()).isEqualTo(down);

    verify(delegate).check();
  }

  @Test void delegatesIsOverCapacity() {
    Exception wayOver = new RejectedExecutionException();
    when(delegate.isOverCapacity(wayOver)).thenReturn(true);

    assertThat(forwarder.isOverCapacity(wayOver)).isEqualTo(true);

    verify(delegate).isOverCapacity(wayOver);
  }

  @Test void delegatesClose() throws IOException {
    doNothing().when(delegate).close();

    forwarder.close();

    verify(delegate).close();
  }

  @Test void delegatesSpanStore() {
    SpanStore spanStore = mock(SpanStore.class);
    when(delegate.spanStore()).thenReturn(spanStore);

    assertThat(forwarder.spanStore()).isEqualTo(spanStore);

    verify(delegate).spanStore();
  }

  @Test void delegatesAutocompleteTags() {
    AutocompleteTags autocompleteTags = mock(AutocompleteTags.class);
    when(delegate.autocompleteTags()).thenReturn(autocompleteTags);

    assertThat(forwarder.autocompleteTags()).isEqualTo(autocompleteTags);

    verify(delegate).autocompleteTags();
  }

  @Test void delegatesServiceAndSpanNames() {
    ServiceAndSpanNames serviceAndSpanNames = mock(ServiceAndSpanNames.class);
    when(delegate.serviceAndSpanNames()).thenReturn(serviceAndSpanNames);

    assertThat(forwarder.serviceAndSpanNames()).isEqualTo(serviceAndSpanNames);

    verify(delegate).serviceAndSpanNames();
  }

  @Test void delegatesSpanConsumer() {
    SpanConsumer spanConsumer = mock(SpanConsumer.class);
    when(delegate.spanConsumer()).thenReturn(spanConsumer);

    assertThat(forwarder.spanConsumer()).isEqualTo(spanConsumer);

    verify(delegate).spanConsumer();
  }
}
