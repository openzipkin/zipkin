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
package zipkin.internal;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.isA;

public class CallbackCaptorTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void getIsUninterruptable() {
    AtomicBoolean returned = new AtomicBoolean();
    CallbackCaptor<String> captor = new CallbackCaptor<>();
    Thread thread = new Thread(() -> {
      captor.get();
      returned.set(true);
    });
    thread.start();
    thread.interrupt();

    assertThat(thread.isInterrupted()).isTrue();
    assertThat(returned.get()).isFalse();
  }

  @Test
  public void onSuccessReturns() {
    CallbackCaptor<String> captor = new CallbackCaptor<>();
    captor.onSuccess("foo");

    assertThat(captor.get()).isEqualTo("foo");
  }

  @Test
  public void onError_propagatesRuntimeException() {
    CallbackCaptor<String> captor = new CallbackCaptor<>();
    captor.onError(new IllegalStateException());

    thrown.expect(IllegalStateException.class);
    captor.get();
  }

  @Test
  public void onError_propagatesError() {
    CallbackCaptor<String> captor = new CallbackCaptor<>();
    captor.onError(new LinkageError());

    thrown.expect(LinkageError.class);
    captor.get();
  }

  @Test
  public void onError_wrapsCheckedExceptions() {
    CallbackCaptor<String> captor = new CallbackCaptor<>();
    captor.onError(new IOException());

    thrown.expect(RuntimeException.class);
    thrown.expectCause(isA(IOException.class));
    captor.get();
  }
}
