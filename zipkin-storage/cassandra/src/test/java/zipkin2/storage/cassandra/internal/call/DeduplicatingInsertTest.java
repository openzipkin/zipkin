/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.internal.call;

import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import zipkin2.Call;
import zipkin2.Callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.mock;

public class DeduplicatingInsertTest {
  @Test void dedupesSameCalls() throws Exception {
    TestFactory testFactory = new TestFactory();

    List<Call<Void>> calls = new ArrayList<>();
    testFactory.maybeAdd("foo", calls);
    testFactory.maybeAdd("bar", calls);
    testFactory.maybeAdd("foo", calls);
    testFactory.maybeAdd("bar", calls);
    testFactory.maybeAdd("bar", calls);
    assertThat(calls).hasSize(2);

    for (Call<Void> call : calls) {
      call.execute();
    }
    assertThat(testFactory.values).containsExactly("foo", "bar");
  }

  Callback<Void> assertFailOnError = new Callback<Void>() {
    @Override public void onSuccess(Void value) {
    }

    @Override public void onError(Throwable t) {
      throw (AssertionError) t;
    }
  };

  @Test void enqueuesInOrder() {
    TestFactory testFactory = new TestFactory();

    List<Call<Void>> calls = new ArrayList<>();
    testFactory.maybeAdd("foo", calls);
    testFactory.maybeAdd("bar", calls);

    for (Call<Void> call : calls) {
      call.enqueue(assertFailOnError);
    }
    assertThat(testFactory.values).containsExactly("foo", "bar");
  }

  @Disabled("Flakey: https://github.com/openzipkin/zipkin/issues/3255")
  @Test void exceptionsInvalidate_enqueue() {
    TestFactory testFactory = new TestFactory();

    List<Call<Void>> calls = new ArrayList<>();
    testFactory.maybeAdd("foo", calls);
    testFactory.maybeAdd("bar", calls);

    testFactory.failValue.set("foo");

    try {
      calls.get(0).enqueue(assertFailOnError);
      failBecauseExceptionWasNotThrown(AssertionError.class);
    } catch (AssertionError e) {
    }

    calls.get(1).enqueue(assertFailOnError);
    assertThat(testFactory.values).containsExactly("bar");

    calls.clear();
    testFactory.maybeAdd("foo", calls);
    assertThat(calls).isNotEmpty(); // invalidates on exception

    calls.get(0).enqueue(assertFailOnError);
    assertThat(testFactory.values).containsExactly("bar", "foo");
  }

  @Test void exceptionsInvalidate_execute() throws Exception {
    TestFactory testFactory = new TestFactory();

    List<Call<Void>> calls = new ArrayList<>();
    testFactory.maybeAdd("foo", calls);
    testFactory.maybeAdd("bar", calls);

    testFactory.failValue.set("foo");

    try {
      calls.get(0).execute();
      failBecauseExceptionWasNotThrown(AssertionError.class);
    } catch (AssertionError e) {
    }

    calls.get(1).execute();
    assertThat(testFactory.values).containsExactly("bar");

    calls.clear();
    testFactory.maybeAdd("foo", calls);
    assertThat(calls).isNotEmpty(); // invalidates on exception

    calls.get(0).execute();
    assertThat(testFactory.values).containsExactly("bar", "foo");
  }

  static final class TestFactory extends DeduplicatingInsert.Factory<String> {
    List<String> values = new ArrayList<>();
    AtomicReference<String> failValue = new AtomicReference<>();

    TestFactory() {
      super(1000, 1000);
    }

    @Override protected Call<Void> newCall(String string) {
      return new TestDeduplicatingInsert(this, string);
    }
  }

  static final class TestDeduplicatingInsert extends DeduplicatingInsert<String> {
    final TestFactory factory;

    TestDeduplicatingInsert(TestFactory factory, String input) {
      super(factory.delayLimiter, input);
      this.factory = factory;
    }

    @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
      if (input.equals(factory.failValue.get())) {
        factory.failValue.set(null);
        CompletableFuture<AsyncResultSet> result = new CompletableFuture<>();
        result.completeExceptionally(new AssertionError());
        return result;
      }
      factory.values.add(input);
      return CompletableFuture.completedFuture(mock(AsyncResultSet.class));
    }

    @Override public Call<Void> clone() {
      return new TestDeduplicatingInsert(factory, input);
    }
  }
}
