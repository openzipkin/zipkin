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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import zipkin.storage.Callback;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class LenientDoubleCallbackTest {

  @Rule public MockitoRule mocks = MockitoJUnit.rule();

  @Mock Logger log;
  @Mock Callback delegate;
  LenientDoubleCallback callback;

  @Before public void setUp() {
    callback = spy(new LenientDoubleCallback(log, delegate) {
      @Override Object merge(Object v1, Object v2) {
        return v1;
      }
    });
  }

  @Test public void merges() throws Exception {
    callback.onSuccess("1");
    callback.onSuccess("2");

    verify(delegate).onSuccess("1");
    verify(callback).merge("1", "2");
  }

  @Test public void okWhenLeftFails() throws Exception {
    RuntimeException throwable = new RuntimeException();
    callback.onError(throwable);
    callback.onSuccess("2");

    verify(log).log(Level.INFO, "first error", throwable);
    verify(delegate).onSuccess("2");
    verifyNoMoreInteractions(log);
  }

  @Test public void okWhenRightFails() throws Exception {
    callback.onSuccess("1");
    callback.onError(new RuntimeException());

    verify(delegate).onSuccess("1");
    verifyNoMoreInteractions(log);
  }

  @Test public void exceptionWhenBothFail() throws Exception {
    IllegalArgumentException exception1 = new IllegalArgumentException();
    IllegalStateException exception2 = new IllegalStateException();
    callback.onError(exception1);
    callback.onError(exception2);

    verify(log).log(Level.INFO, "first error", exception1);
    verify(delegate).onError(exception2);
  }
}
