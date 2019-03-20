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
import com.google.common.util.concurrent.UncheckedTimeoutException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.internal.Nullable;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class RestCallTest {
  @Rule
  public MockWebServer mws = new MockWebServer();

  RestClient client =
    RestClient.builder(new HttpHost(mws.getHostName(), mws.getPort())).build();
  Request request = RequestBuilder.get("/").build();

  @After
  public void close() throws IOException {
    client.close();
  }

  private <V> RestCall<V> newCall(Request request, ResponseConverter<V> responseConverter) {
    return new RestCall<>(client, request, responseConverter);
  }

  @Test
  public void propagatesOnDispatcherThreadWhenFatal() throws Exception {
    mws.enqueue(new MockResponse());

    final LinkedBlockingQueue<Object> q = new LinkedBlockingQueue<>();
    newCall(request, b -> {
      throw new LinkageError();
    }).enqueue(new Callback<Object>() {
      @Override public void onSuccess(@Nullable Object value) {
        q.add(value);
      }

      @Override public void onError(Throwable t) {
        q.add(t);
      }
    });

    SimpleTimeLimiter timeLimiter = new SimpleTimeLimiter();
    try {
      timeLimiter.callWithTimeout(q::take, 100, TimeUnit.MILLISECONDS, true);
      failBecauseExceptionWasNotThrown(UncheckedTimeoutException.class);
    } catch (UncheckedTimeoutException expected) {
    }
  }

  @Test
  public void executionException_conversionException() throws Exception {
    mws.enqueue(new MockResponse());

    Call<?> call = newCall(request, b -> {
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
  public void executionException_httpFailure() throws Exception {
    mws.enqueue(new MockResponse().setResponseCode(500));

    Call<?> call = newCall(request, b -> null);

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException expected) {
      assertThat(expected).isInstanceOf(IllegalStateException.class);
    }
  }
}
