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
package zipkin2.elasticsearch.internal.client; // to access package-private stuff

import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.HttpClientBuilder;
import com.linecorp.armeria.client.UnprocessedRequestException;
import com.linecorp.armeria.client.endpoint.EndpointGroupException;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.common.logging.RequestLogAvailability;
import com.linecorp.armeria.testing.junit.server.mock.MockWebServerExtension;
import com.linecorp.armeria.unsafe.ByteBufHttpData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import java.io.FileNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.internal.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static zipkin2.TestObjects.UTF_8;

class HttpCallTest {
  static final HttpCall.BodyConverter<Object> NULL = (parser, contentString) -> null;

  private static final AggregatedHttpResponse SUCCESS_RESPONSE =
    AggregatedHttpResponse.of(HttpStatus.OK);

  @RegisterExtension static MockWebServerExtension server = new MockWebServerExtension();

  private static final AggregatedHttpRequest REQUEST =
    AggregatedHttpRequest.of(HttpMethod.GET, "/");

  HttpCall.Factory http;

  @BeforeEach void setUp() {
    http = new HttpCall.Factory(HttpClient.of(server.httpUri("/")));
  }

  @Test void emptyContent() throws Exception {
    server.enqueue(AggregatedHttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    assertThat(http.newCall(REQUEST, (parser, contentString) -> fail(), "test").execute()).isNull();

    server.enqueue(AggregatedHttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));
    CompletableCallback<String> future = new CompletableCallback<>();
    http.newCall(REQUEST, (parser, contentString) -> "hello", "test").enqueue(future);
    assertThat(future.join()).isNull();
  }

  @Test void propagatesOnDispatcherThreadWhenFatal() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    final LinkedBlockingQueue<Object> q = new LinkedBlockingQueue<>();
    http.newCall(REQUEST, (parser, contentString) -> {
      throw new LinkageError();
    }, "test").enqueue(new Callback<Object>() {
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

  @Test void executionException_conversionException() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    Call<?> call = http.newCall(REQUEST, (parser, contentString) -> {
      throw new IllegalArgumentException("eeek");
    }, "test");

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException expected) {
      assertThat(expected).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test void cloned() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    Call<?> call = http.newCall(REQUEST, (parser, contentString) -> null, "test");
    call.execute();

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException expected) {
      assertThat(expected).isInstanceOf(IllegalStateException.class);
    }

    server.enqueue(SUCCESS_RESPONSE);

    call.clone().execute();
  }

  @Test void executionException_5xx() throws Exception {
    server.enqueue(AggregatedHttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR));

    Call<?> call = http.newCall(REQUEST, NULL, "test");

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(RuntimeException.class);
    } catch (RuntimeException expected) {
      assertThat(expected).hasMessage("response for / failed: 500 Internal Server Error");
    }
  }

  @Test void executionException_404() throws Exception {
    server.enqueue(AggregatedHttpResponse.of(HttpStatus.NOT_FOUND));

    Call<?> call = http.newCall(REQUEST, NULL, "test");

    try {
      call.execute();
      failBecauseExceptionWasNotThrown(FileNotFoundException.class);
    } catch (FileNotFoundException expected) {
      assertThat(expected).hasMessage("/");
    }
  }

  @Test void releasesAllReferencesToByteBuf() {
    // Force this to be a ref-counted response
    byte[] message = "{\"Message\":\"error\"}".getBytes(UTF_8);
    ByteBuf encodedBuf = PooledByteBufAllocator.DEFAULT.buffer(message.length);
    encodedBuf.writeBytes(message);
    AggregatedHttpResponse response = AggregatedHttpResponse.of(
      ResponseHeaders.of(HttpStatus.FORBIDDEN),
      new ByteBufHttpData(encodedBuf, true)
    );

    HttpCall<Object> call = http.newCall(REQUEST, NULL, "test");

    // Invoke the parser directly because using the fake server will not result in ref-counted
    assertThatThrownBy(() -> call.parseResponse(response, NULL)).hasMessage("error");
    assertThat(encodedBuf.refCnt()).isEqualTo(0);
  }

  // For simplicity, we also parse messages from AWS Elasticsearch, as it prevents copy/paste.
  @Test void executionException_message() throws Exception {
    Map<AggregatedHttpResponse, String> responseToMessage = new LinkedHashMap<>();
    responseToMessage.put(AggregatedHttpResponse.of(
      ResponseHeaders.of(HttpStatus.FORBIDDEN),
      HttpData.ofUtf8(
        "{\"Message\":\"User: anonymous is not authorized to perform: es:ESHttpGet\"}")
    ), "User: anonymous is not authorized to perform: es:ESHttpGet");
    responseToMessage.put(AggregatedHttpResponse.of(
      ResponseHeaders.of(HttpStatus.FORBIDDEN)
    ), "response for / failed: 403 Forbidden");
    responseToMessage.put(AggregatedHttpResponse.of(
      ResponseHeaders.of(HttpStatus.BAD_GATEWAY),
      HttpData.ofUtf8("Message: sleet") // note: not json
    ), "response for / failed: Message: sleet"); // In this case, we give request context

    Call<?> call = http.newCall(REQUEST, NULL, "test");

    for (Map.Entry<AggregatedHttpResponse, String> entry : responseToMessage.entrySet()) {
      server.enqueue(entry.getKey());

      try {
        call.clone().execute();
        failBecauseExceptionWasNotThrown(RuntimeException.class);
      } catch (RuntimeException expected) {
        assertThat(expected).hasMessage(entry.getValue());
      }
    }
  }

  @Test void setsCustomName() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    AtomicReference<RequestLog> log = new AtomicReference<>();
    http = new HttpCall.Factory(new HttpClientBuilder(server.httpUri("/"))
      .decorator((client, ctx, req) -> {
        ctx.log().addListener(log::set, RequestLogAvailability.COMPLETE);
        return client.execute(ctx, req);
      })
      .build());

    http.newCall(REQUEST, NULL, "custom-name").execute();

    await().untilAsserted(() -> assertThat(log).doesNotHaveValue(null));
    assertThat(log.get().context().attr(HttpCall.NAME).get())
      .isEqualTo("custom-name");
  }

  @Test void wrongScheme() {
    server.enqueue(SUCCESS_RESPONSE);

    http = new HttpCall.Factory(new HttpClientBuilder("https://localhost:" + server.httpPort())
      .build());

    assertThatThrownBy(() -> http.newCall(REQUEST, NULL, "test").execute())
      .isInstanceOf(RejectedExecutionException.class)
      .hasMessage("ClosedSessionException");
  }

  @Test void unprocessedRequest() {
    server.enqueue(SUCCESS_RESPONSE);

    http = new HttpCall.Factory(new HttpClientBuilder(server.httpUri("/"))
      .decorator((client, ctx, req) -> {
        throw new UnprocessedRequestException("Could not process request.",
          new EndpointGroupException("No endpoints"));
      })
      .build());

    assertThatThrownBy(() -> http.newCall(REQUEST, NULL, "test").execute())
      .isInstanceOf(RejectedExecutionException.class)
      .hasMessage("No endpoints");
  }

  @Test void throwsRuntimeExceptionAsReasonWhenPresent() {
    String body =
      "{\"error\":{\"root_cause\":[{\"type\":\"illegal_argument_exception\",\"reason\":\"Fielddata is disabled on text fields by default. Set fielddata=true on [spanName] in order to load fielddata in memory by uninverting the inverted index. Note that this can however use significant memory. Alternatively use a keyword field instead.\"}],\"type\":\"search_phase_execution_exception\",\"reason\":\"all shards failed\",\"phase\":\"query\",\"grouped\":true,\"failed_shards\":[{\"shard\":0,\"index\":\"zipkin-2017-05-14\",\"node\":\"IqceAwZnSvyv0V0xALkEnQ\",\"reason\":{\"type\":\"illegal_argument_exception\",\"reason\":\"Fielddata is disabled on text fields by default. Set fielddata=true on [spanName] in order to load fielddata in memory by uninverting the inverted index. Note that this can however use significant memory. Alternatively use a keyword field instead.\"}}]},\"status\":400}";
    server.enqueue(
      AggregatedHttpResponse.of(ResponseHeaders.of(HttpStatus.BAD_REQUEST), HttpData.ofUtf8(body))
    );

    assertThatThrownBy(() -> http.newCall(REQUEST, NULL, "test").execute())
      .isInstanceOf(RuntimeException.class)
      .hasMessage(
        "Fielddata is disabled on text fields by default. Set fielddata=true on [spanName] in order to load fielddata in memory by uninverting the inverted index. Note that this can however use significant memory. Alternatively use a keyword field instead.");
  }

  @Test void streamingContent() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    HttpCall.RequestSupplier supplier = new HttpCall.RequestSupplier() {
      @Override public RequestHeaders headers() {
        return RequestHeaders.of(HttpMethod.POST, "/");
      }

      @Override public void writeBody(HttpCall.RequestStream requestStream) {
        requestStream.tryWrite(HttpData.ofUtf8("hello"));
        requestStream.tryWrite(HttpData.ofUtf8(" world"));
      }
    };

    http.newCall(supplier, NULL, "test").execute();

    AggregatedHttpRequest request = server.takeRequest().request();
    assertThat(request.method()).isEqualTo(HttpMethod.POST);
    assertThat(request.path()).isEqualTo("/");
    assertThat(request.contentUtf8()).isEqualTo("hello world");
  }

  // TODO(adriancole): Find a home for this generic conversion between Call and Java 8.
  static final class CompletableCallback<T> extends CompletableFuture<T> implements Callback<T> {

    @Override public void onSuccess(T value) {
      complete(value);
    }

    @Override public void onError(Throwable t) {
      completeExceptionally(t);
    }
  }
}
