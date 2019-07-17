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
package zipkin2.elasticsearch.internal.client;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.internal.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class HttpCallTest {

  private static final AtomicReference<AggregatedHttpResponse> MOCK_RESPONSE =
    new AtomicReference<>();
  private static final AggregatedHttpResponse SUCCESS_RESPONSE =
    AggregatedHttpResponse.of(HttpStatus.OK);

  @ClassRule public static ServerRule server = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) {
      sb.service("/", ((ctx, req) -> HttpResponse.of(MOCK_RESPONSE.get())));
    }
  };

  private static final AggregatedHttpRequest REQUEST =
    AggregatedHttpRequest.of(HttpMethod.GET, "/");

  HttpCall.Factory http;

  @Before public void setUp() {
    http = new HttpCall.Factory(HttpClient.of(server.httpUri("/")), Integer.MAX_VALUE);
  }

  @Test
  public void propagatesOnDispatcherThreadWhenFatal() throws Exception {
    MOCK_RESPONSE.set(SUCCESS_RESPONSE);

    final LinkedBlockingQueue<Object> q = new LinkedBlockingQueue<>();
    http.newCall(REQUEST, b -> {
      throw new LinkageError();
    }).enqueue(new Callback<Object>() {
      @Override public void onSuccess(@Nullable Object value) {
        q.add(value);
      }

      @Override public void onError(Throwable t) {
        q.add(t);
      }
    });

    ExecutorService cached = Executors.newCachedThreadPool();
    SimpleTimeLimiter timeLimiter = SimpleTimeLimiter.create(cached);
    try {
      timeLimiter.callWithTimeout(q::take, 100, TimeUnit.MILLISECONDS);
      failBecauseExceptionWasNotThrown(TimeoutException.class);
    } catch (TimeoutException expected) {
    } finally {
      cached.shutdownNow();
    }
  }

  @Test
  public void executionException_conversionException() throws Exception {
    MOCK_RESPONSE.set(SUCCESS_RESPONSE);

    Call<?> call = http.newCall(REQUEST, b -> {
      throw new IllegalArgumentException("eeek");
    });

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException expected) {
      assertThat(expected).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void cloned() throws Exception {
    MOCK_RESPONSE.set(SUCCESS_RESPONSE);

    Call<?> call = http.newCall(REQUEST, b -> null);
    call.execute();

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException expected) {
      assertThat(expected).isInstanceOf(IllegalStateException.class);
    }

    MOCK_RESPONSE.set(SUCCESS_RESPONSE);

    call.clone().execute();
  }

  @Test
  public void executionException_httpFailure() throws Exception {
    MOCK_RESPONSE.set(AggregatedHttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR));

    Call<?> call = http.newCall(REQUEST, b -> null);

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException expected) {
      assertThat(expected).isInstanceOf(IllegalStateException.class);
    }
  }
}
