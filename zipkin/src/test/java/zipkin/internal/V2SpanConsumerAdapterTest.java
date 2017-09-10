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
package zipkin.internal;

import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import zipkin.TestObjects;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;
import zipkin2.Call;
import zipkin2.storage.SpanConsumer;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class V2SpanConsumerAdapterTest {

  @Rule public MockitoRule mocks = MockitoJUnit.rule();
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Mock SpanConsumer delegate;
  @Mock Call<Void> call;
  @Mock Callback<Void> callback;

  AsyncSpanConsumer asyncSpanConsumer;

  @Before public void setUp() {
    asyncSpanConsumer = new V2SpanConsumerAdapter(delegate);
    when(delegate.accept(any(List.class))).thenReturn(call);
  }

  @Test public void accept_success() {
    doNothing().when(call).enqueue(any(zipkin2.Callback.class));

    asyncSpanConsumer.accept(TestObjects.TRACE, callback);

    verify(call).enqueue(any(zipkin2.Callback.class));
  }

  @Test public void accept_exception() {
    IllegalStateException throwable = new IllegalStateException("failed");
    doAnswer(invocation -> {
      ((zipkin2.Callback) invocation.getArguments()[0]).onError(throwable);
      return invocation;
    }).when(call).enqueue(any(zipkin2.Callback.class));

    asyncSpanConsumer.accept(TestObjects.TRACE, callback);

    verify(callback).onError(throwable);
  }
}
