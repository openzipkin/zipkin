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

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpClientTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  @Rule
  public MockWebServer es = new MockWebServer();

  HttpClient client = (HttpClient) new HttpClientBuilder(new OkHttpClient())
      .hosts(asList(es.url("").toString()))
      .buildFactory().create("zipkin-*");

  @After
  public void close() throws IOException {
    client.http.dispatcher().executorService().shutdownNow();
  }

  /** Declaring queries alphabetically helps simplify amazon signature logic */
  @Test
  public void lenientSearchOrdersQueryAlphabetically() throws Exception {
    es.enqueue(new MockResponse());

    assertThat(client.lenientSearch(new String[] {"zipkin-2016-10-01"}, "span")
        .queryParameterNames())
        .containsExactly("allow_no_indices", "expand_wildcards", "ignore_unavailable");
  }

  @Test
  public void findDependencies() throws Exception {
    es.enqueue(new MockResponse());

    client.findDependencies(new String[] {"zipkin-2016-10-01", "zipkin-2016-10-02"}).get();

    RecordedRequest request = es.takeRequest();
    assertThat(request.getPath())
        .isEqualTo("/zipkin-2016-10-01,zipkin-2016-10-02/dependencylink/_search?allow_no_indices=true&expand_wildcards=open&ignore_unavailable=true");
    assertThat(request.getBody().buffer().readUtf8())
        .isEqualTo("{\n"
            + "  \"query\" : {\n"
            + "    \"match_all\" : { }\n"
            + "  }\n"
            + "}");
  }
}
