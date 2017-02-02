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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ElasticsearchHttpStorageTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  @Rule
  public MockWebServer es = new MockWebServer();

  ElasticsearchHttpStorage storage = ElasticsearchHttpStorage.builder()
      .hosts(asList(es.url("").toString()))
      .build();

  @After
  public void close() throws IOException {
    storage.close();
  }

  @Test
  public void memoizesIndexTemplate() throws Exception {
    es.enqueue(new MockResponse().setBody("{\"version\":{\"number\":\"2.4.0\"}}"));
    es.enqueue(new MockResponse()); // get template
    es.enqueue(new MockResponse()); // dependencies request
    es.enqueue(new MockResponse()); // dependencies request

    long endTs = storage.indexNameFormatter().parseDate("2016-10-02");
    storage.spanStore().getDependencies(endTs, TimeUnit.DAYS.toMillis(1));
    storage.spanStore().getDependencies(endTs, TimeUnit.DAYS.toMillis(1));

    es.takeRequest(); // get version
    es.takeRequest(); // get template
    assertThat(es.takeRequest().getPath())
        .startsWith("/zipkin-2016-10-01,zipkin-2016-10-02/dependencylink/_search");
    assertThat(es.takeRequest().getPath())
        .startsWith("/zipkin-2016-10-01,zipkin-2016-10-02/dependencylink/_search");
  }
}
