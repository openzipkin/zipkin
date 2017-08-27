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
package zipkin.storage.elasticsearch.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.internal.tls.SslClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin.Component;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ElasticsearchHttpStorageTest {
  @Rule public MockWebServer es = new MockWebServer();

  ElasticsearchHttpStorage storage = ElasticsearchHttpStorage.builder()
    .hosts(asList(es.url("").toString()))
    .build();

  @After public void close() throws IOException {
    storage.close();
  }

  @Test public void memoizesIndexTemplate() throws Exception {
    es.enqueue(new MockResponse().setBody("{\"version\":{\"number\":\"2.4.0\"}}"));
    es.enqueue(new MockResponse()); // get span template
    es.enqueue(new MockResponse()); // get dependency template
    es.enqueue(new MockResponse()); // dependencies request
    es.enqueue(new MockResponse()); // dependencies legacy request
    es.enqueue(new MockResponse()); // dependencies request
    es.enqueue(new MockResponse()); // dependencies legacy request

    long endTs = storage.indexNameFormatter().parseDate("2016-10-02");
    storage.spanStore().getDependencies(endTs, TimeUnit.DAYS.toMillis(1));
    storage.spanStore().getDependencies(endTs, TimeUnit.DAYS.toMillis(1));

    es.takeRequest(); // get version
    es.takeRequest(); // get span template
    es.takeRequest(); // get dependency template

    String currentRequest = "/zipkin:dependency-2016-10-01,zipkin:dependency-2016-10-02/_search";
    String legacyRequest = "/zipkin-2016-10-01,zipkin-2016-10-02/dependencylink/_search";
    for (int i = 0; i < 2; i++) {
      // with dual reads, order can be inconsistent.
      String request1 = es.takeRequest().getPath();
      String request2 = es.takeRequest().getPath();
      if (request1.startsWith(currentRequest)) {
        assertThat(request2)
          .startsWith(legacyRequest);
      } else {
        assertThat(request1)
          .startsWith(legacyRequest);
        assertThat(request2)
          .startsWith(currentRequest);
      }
    }
  }

  @Test public void ensureIndexTemplates_when6xNoLegacySupport() throws Exception {
    es.enqueue(new MockResponse().setBody("{\"version\":{\"number\":\"6.0.0\"}}"));
    es.enqueue(new MockResponse()); // get span template
    es.enqueue(new MockResponse()); // get dependency template

    // check this isn't the double reading span store
    assertThat(storage.asyncSpanStore().getClass().getSimpleName())
      .isEqualTo("V2SpanStoreAdapter");

    es.takeRequest(); // get version
    es.takeRequest(); // get span template
    es.takeRequest(); // get dependency template
  }

  @Test public void ensureIndexTemplates_2x() throws Exception {
    storage.close();
    storage = ElasticsearchHttpStorage.builder()
      .hosts(asList(es.url("").toString()))
      .build();

    es.enqueue(new MockResponse().setBody("{\"version\":{\"number\":\"2.2.0\"}}"));
    es.enqueue(new MockResponse()); // get span template
    es.enqueue(new MockResponse()); // get dependency template

    // check that we do double-reads on the legacy and new format
    assertThat(storage.asyncSpanStore().getClass().getSimpleName())
      .isEqualTo("LenientDoubleCallbackAsyncSpanStore");

    es.takeRequest(); // get version
    es.takeRequest(); // get span template
    es.takeRequest(); // get dependency template
  }

  @Test public void ensureIndexTemplates_2x_legacyReadsDisabled() throws Exception {
    storage.close();
    storage = ElasticsearchHttpStorage.builder()
      .hosts(asList(es.url("").toString()))
      .legacyReadsEnabled(false)
      .build();

    es.enqueue(new MockResponse().setBody("{\"version\":{\"number\":\"2.2.0\"}}"));
    es.enqueue(new MockResponse()); // get span template
    es.enqueue(new MockResponse()); // get dependency template

    // check this isn't the double reading span store
    assertThat(storage.asyncSpanStore().getClass().getSimpleName())
      .isEqualTo("V2SpanStoreAdapter");

    es.takeRequest(); // get version
    es.takeRequest(); // get span template
    es.takeRequest(); // get dependency template
  }

  String healthResponse = "{\n"
    + "  \"cluster_name\": \"elasticsearch_zipkin\",\n"
    + "  \"status\": \"yellow\",\n"
    + "  \"timed_out\": false,\n"
    + "  \"number_of_nodes\": 1,\n"
    + "  \"number_of_data_nodes\": 1,\n"
    + "  \"active_primary_shards\": 5,\n"
    + "  \"active_shards\": 5,\n"
    + "  \"relocating_shards\": 0,\n"
    + "  \"initializing_shards\": 0,\n"
    + "  \"unassigned_shards\": 5,\n"
    + "  \"delayed_unassigned_shards\": 0,\n"
    + "  \"number_of_pending_tasks\": 0,\n"
    + "  \"number_of_in_flight_fetch\": 0,\n"
    + "  \"task_max_waiting_in_queue_millis\": 0,\n"
    + "  \"active_shards_percent_as_number\": 50\n"
    + "}";

  @Test public void check() throws Exception {
    es.enqueue(new MockResponse().setBody(healthResponse));

    assertThat(storage.check())
      .isEqualTo(Component.CheckResult.OK);
  }

  @Test public void check_oneHostDown() throws Exception {
    storage.close();
    OkHttpClient client = new OkHttpClient.Builder()
      .connectTimeout(100, TimeUnit.MILLISECONDS)
      .build();
    storage = ElasticsearchHttpStorage.builder(client)
      .hosts(asList("http://1.2.3.4:" + es.getPort(), es.url("").toString()))
      .build();

    es.enqueue(new MockResponse().setBody(healthResponse));

    assertThat(storage.check())
      .isEqualTo(Component.CheckResult.OK);
  }

  @Test public void check_ssl() throws Exception {
    storage.close();
    SslClient sslClient = SslClient.localhost();
    OkHttpClient client = new OkHttpClient.Builder()
      .sslSocketFactory(sslClient.socketFactory, sslClient.trustManager)
      .build();
    es.useHttps(sslClient.socketFactory, false);

    storage = ElasticsearchHttpStorage.builder(client)
      .hosts(asList(es.url("").toString()))
      .build();

    es.enqueue(new MockResponse().setBody(healthResponse));

    assertThat(storage.check())
      .isEqualTo(Component.CheckResult.OK);

    assertThat(es.takeRequest().getTlsVersion())
      .isNotNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void multipleSslNotYetSupported() throws Exception {
    storage.close();
    SslClient sslClient = SslClient.localhost();
    OkHttpClient client = new OkHttpClient.Builder()
      .sslSocketFactory(sslClient.socketFactory, sslClient.trustManager)
      .build();
    es.useHttps(sslClient.socketFactory, false);

    storage = ElasticsearchHttpStorage.builder(client)
      .hosts(asList("https://1.2.3.4:" + es.getPort(), es.url("").toString()))
      .build();

    storage.check();
  }
}
