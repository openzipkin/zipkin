/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import zipkin.server.ZipkinServer;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.internal.TracesAdapter;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    "zipkin.query.timeout=1ms"
  }
)
class ITZipkinServerTimeout {
  static final List<Span> TRACE = List.of(TestObjects.CLIENT_SPAN);

  SlowSpanStore spanStore;

  @MockBean StorageComponent storage;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @BeforeEach void init() {
    spanStore = new SlowSpanStore();
    when(storage.spanStore()).thenReturn(spanStore);
    when(storage.traces()).thenReturn(new TracesAdapter(spanStore));
  }

  @Test void getTrace() throws Exception {
    spanStore.storage.accept(TRACE).execute();

    Response response = get("/api/v2/trace/" + TRACE.get(0).traceId());
    assertThat(response.isSuccessful()).isFalse();

    assertThat(response.code()).isEqualTo(500);
  }

  Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .build()).execute();
  }

  static String url(Server server, String path) {
    return "http://localhost:" + server.activeLocalPort() + path;
  }

  static class SlowSpanStore implements SpanStore {
    final InMemoryStorage storage = InMemoryStorage.newBuilder().build();

    @Override public Call<List<List<Span>>> getTraces(QueryRequest request) {
      sleep();
      return storage.spanStore().getTraces(request);
    }

    @Override public Call<List<Span>> getTrace(String traceId) {
      sleep();
      return storage.spanStore().getTrace(traceId);
    }

    @Override public Call<List<String>> getServiceNames() {
      sleep();
      return storage.spanStore().getServiceNames();
    }

    @Override public Call<List<String>> getSpanNames(String serviceName) {
      sleep();
      return storage.spanStore().getSpanNames(serviceName);
    }

    @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
      sleep();
      return storage.spanStore().getDependencies(endTs, lookback);
    }

    static void sleep() {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new Error(e);
      }
    }
  }
}
