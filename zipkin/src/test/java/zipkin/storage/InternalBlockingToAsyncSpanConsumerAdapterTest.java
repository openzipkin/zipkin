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
package zipkin.storage;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import zipkin.TestObjects;
import zipkin.internal.CallbackCaptor;
import zipkin.storage.StorageAdapters.SpanConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

public class InternalBlockingToAsyncSpanConsumerAdapterTest {

  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private SpanConsumer spanConsumer;

  private AsyncSpanConsumer asyncSpanConsumer;

  @Before
  public void setUp() {
    // run on calling thread so we don't have to make a complex callback captor
    asyncSpanConsumer = new InternalBlockingToAsyncSpanConsumerAdapter(spanConsumer, Runnable::run);
  }

  @Test
  public void accept_success() {
    CallbackCaptor<Void> captor = new CallbackCaptor<>();
    asyncSpanConsumer.accept(TestObjects.TRACE, captor);
    assertThat(captor.get()).isNull();
  }

  @Test
  public void accept_exception() {
    thrown.expect(IllegalStateException.class);
    doThrow(new IllegalStateException("failed"))
        .when(spanConsumer).accept(TestObjects.TRACE);

    CallbackCaptor<Void> captor = new CallbackCaptor<>();
    asyncSpanConsumer.accept(TestObjects.TRACE, captor);
    captor.get();
  }
}
