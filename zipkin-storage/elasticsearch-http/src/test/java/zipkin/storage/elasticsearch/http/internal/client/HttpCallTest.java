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
package zipkin.storage.elasticsearch.http.internal.client;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin.internal.CallbackCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class HttpCallTest {
  @Rule
  public MockWebServer mws = new MockWebServer();

  HttpCall.Factory http = new HttpCall.Factory(new OkHttpClient(), mws.url(""));
  Request request = new Request.Builder().url(http.baseUrl).build();
  CallbackCaptor<Object> callback = new CallbackCaptor<>();

  @After
  public void close() throws IOException {
    http.close();
  }

  @Test
  public void propagatesOnDispatcherThreadWhenFatal() throws Exception {
    mws.enqueue(new MockResponse());

    http.newCall(request, b -> {
      throw new LinkageError();
    }).submit(callback);

    SimpleTimeLimiter timeLimiter = new SimpleTimeLimiter();
    try {
      timeLimiter.callWithTimeout(callback::get, 100, TimeUnit.MILLISECONDS, true);
      failBecauseExceptionWasNotThrown(UncheckedTimeoutException.class);
    } catch (UncheckedTimeoutException expected) {
    }
  }

  @Test
  public void executionException_conversionException() throws Exception {
    mws.enqueue(new MockResponse());

    http.newCall(request, b -> {
      throw new IllegalArgumentException("eeek");
    }).submit(callback);

    try {
      callback.get();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException expected) {
      assertThat(expected).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void executionException_httpFailure() throws Exception {
    mws.enqueue(new MockResponse().setResponseCode(500));

    http.newCall(request, b -> null).submit(callback);

    try {
      callback.get();
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException expected) {
      assertThat(expected).isInstanceOf(IllegalStateException.class);
    }
  }
}
