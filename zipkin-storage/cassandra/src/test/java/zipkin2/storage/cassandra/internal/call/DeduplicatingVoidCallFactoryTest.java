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
package zipkin2.storage.cassandra.internal.call;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.Test;
import zipkin2.Call;
import zipkin2.Callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class DeduplicatingVoidCallFactoryTest {
  List<String> values = new ArrayList<>();
  AtomicReference<String> failValue = new AtomicReference<>();

  class AddToValueCall extends Call.Base<Void> {
    final String value;

    AddToValueCall(String value) {
      this.value = value;
    }

    @Override public Call<Void> clone() {
      return new AddToValueCall(value);
    }

    @Override protected Void doExecute() {
      if (value.equals(failValue.get())) {
        failValue.set(null);
        throw new AssertionError();
      }
      values.add(value);
      return null;
    }

    @Override protected void doEnqueue(Callback<Void> callback) {
      if (value.equals(failValue.get())) {
        failValue.set(null);
        callback.onError(new AssertionError());
        return;
      }
      values.add(value);
      callback.onSuccess(null);
    }
  }

  Function<String, Call<Void>> delegate = AddToValueCall::new;
  TestDeduplicatingVoidCallFactory callFactory = new TestDeduplicatingVoidCallFactory(delegate);

  @Test public void dedupesSameCalls() throws Exception {
    List<Call<Void>> calls = new ArrayList<>();
    callFactory.maybeAdd("foo", calls);
    callFactory.maybeAdd("bar", calls);
    callFactory.maybeAdd("foo", calls);
    callFactory.maybeAdd("bar", calls);
    callFactory.maybeAdd("bar", calls);
    assertThat(calls).hasSize(2);

    for (Call<Void> call : calls) {
      call.execute();
    }
    assertThat(values).containsExactly("foo", "bar");
  }

  Callback<Void> assertFailOnError = new Callback<Void>() {
    @Override public void onSuccess(Void value) {
    }

    @Override public void onError(Throwable t) {
      throw (AssertionError) t;
    }
  };

  @Test public void enqueuesInOrder() {
    List<Call<Void>> calls = new ArrayList<>();
    callFactory.maybeAdd("foo", calls);
    callFactory.maybeAdd("bar", calls);

    for (Call<Void> call : calls) {
      call.enqueue(assertFailOnError);
    }
    assertThat(values).containsExactly("foo", "bar");
  }

  @Test public void exceptionsInvalidate_enqueue() throws Exception {
    List<Call<Void>> calls = new ArrayList<>();
    callFactory.maybeAdd("foo", calls);
    callFactory.maybeAdd("bar", calls);

    failValue.set("foo");

    try {
      calls.get(0).enqueue(assertFailOnError);
      failBecauseExceptionWasNotThrown(AssertionError.class);
    } catch (AssertionError e) {
    }

    calls.get(1).enqueue(assertFailOnError);
    assertThat(values).containsExactly("bar");

    calls.clear();
    callFactory.maybeAdd("foo", calls);
    assertThat(calls).isNotEmpty(); // invalidates on exception

    calls.get(0).enqueue(assertFailOnError);
    assertThat(values).containsExactly("bar", "foo");
  }

  @Test public void exceptionsInvalidate_execute() throws Exception {
    List<Call<Void>> calls = new ArrayList<>();
    callFactory.maybeAdd("foo", calls);
    callFactory.maybeAdd("bar", calls);

    failValue.set("foo");

    try {
      calls.get(0).execute();
      failBecauseExceptionWasNotThrown(AssertionError.class);
    } catch (AssertionError e) {
    }

    calls.get(1).execute();
    assertThat(values).containsExactly("bar");

    calls.clear();
    callFactory.maybeAdd("foo", calls);
    assertThat(calls).isNotEmpty(); // invalidates on exception

    calls.get(0).execute();
    assertThat(values).containsExactly("bar", "foo");
  }

  static class TestDeduplicatingVoidCallFactory extends DeduplicatingVoidCallFactory<String> {
    final Function<String, Call<Void>> delegate;

    TestDeduplicatingVoidCallFactory(Function<String, Call<Void>> delegate) {
      super(1000, 1000);
      this.delegate = delegate;
    }

    @Override protected Call<Void> newCall(String string) {
      return delegate.apply(string);
    }
  }
}
