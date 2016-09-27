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
package zipkin.storage.elasticsearch.http;

import com.google.common.collect.ImmutableList;
import io.searchbox.client.config.exception.CouldNotConnectException;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin.storage.elasticsearch.ElasticsearchStorage;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpElasticsearchStorageTest {
  @Rule
  public MockWebServer es = new MockWebServer();

  ElasticsearchStorage storage = ElasticsearchStorage.builder(new HttpClient.Builder()
      .hosts(ImmutableList.of(es.url("/").toString()))).build();

  @After
  public void close() throws IOException {
    storage.close();
  }

  /** Very important that we don't return wrapped exceptions as otherwise /health endpoint is useless */
  @Test
  public void check_fail_doesntWrapExceptions() throws IOException {
    es.shutdown();

    assertThat(storage.check().ok).isFalse();
    assertThat(storage.check().exception)
        .isInstanceOf(CouldNotConnectException.class);
  }

  @Test
  public void check_ensuresTemplateExistsAndClusterGreen() throws InterruptedException {
    es.enqueue(new MockResponse());
    es.enqueue(new MockResponse().setBody("{\n"
        + "  \"cluster_name\" : \"zipkin\",\n"
        + "  \"status\" : \"green\"\n"
        + "}"));

    assertThat(storage.check().ok).isTrue();

    assertThat(es.takeRequest().getPath()).isEqualTo("/_template/zipkin_template");
    assertThat(es.takeRequest().getPath()).isEqualTo("/_cluster/health/zipkin-*");
  }
}
