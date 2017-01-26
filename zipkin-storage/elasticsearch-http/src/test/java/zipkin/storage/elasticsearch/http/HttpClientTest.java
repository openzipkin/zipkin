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
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.TestObjects;
import zipkin.internal.CallbackCaptor;

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
        .isEqualTo(
            "/zipkin-2016-10-01,zipkin-2016-10-02/dependencylink/_search?allow_no_indices=true&expand_wildcards=open&ignore_unavailable=true");
    assertThat(request.getBody().buffer().readUtf8())
        .isEqualTo("{\n"
            + "  \"size\" : 10000,\n"
            + "  \"query\" : {\n"
            + "    \"match_all\" : { }\n"
            + "  }\n"
            + "}");
  }

  /** Unsupported, but we should test that parsing works */
  @Test
  public void getVersion_1() throws Exception {
    es.enqueue(new MockResponse().setBody("{\n"
        + "  \"status\" : 200,\n"
        + "  \"name\" : \"Shen Kuei\",\n"
        + "  \"cluster_name\" : \"elasticsearch\",\n"
        + "  \"version\" : {\n"
        + "    \"number\" : \"1.7.3\",\n"
        + "    \"build_hash\" : \"05d4530971ef0ea46d0f4fa6ee64dbc8df659682\",\n"
        + "    \"build_timestamp\" : \"2015-10-15T09:14:17Z\",\n"
        + "    \"build_snapshot\" : false,\n"
        + "    \"lucene_version\" : \"4.10.4\"\n"
        + "  },\n"
        + "  \"tagline\" : \"You Know, for Search\"\n"
        + "}"));

    assertThat(client.getVersion()).isEqualTo("1.7.3");
  }

  @Test
  public void getVersion_2() throws Exception {
    es.enqueue(new MockResponse().setBody("{\n"
        + "  \"name\" : \"Kamal\",\n"
        + "  \"cluster_name\" : \"elasticsearch\",\n"
        + "  \"version\" : {\n"
        + "    \"number\" : \"2.4.0\",\n"
        + "    \"build_hash\" : \"ce9f0c7394dee074091dd1bc4e9469251181fc55\",\n"
        + "    \"build_timestamp\" : \"2016-08-29T09:14:17Z\",\n"
        + "    \"build_snapshot\" : false,\n"
        + "    \"lucene_version\" : \"5.5.2\"\n"
        + "  },\n"
        + "  \"tagline\" : \"You Know, for Search\"\n"
        + "}"));

    assertThat(client.getVersion()).isEqualTo("2.4.0");
  }

  @Test
  public void getVersion_5() throws Exception {
    es.enqueue(new MockResponse().setBody("{\n"
        + "  \"name\" : \"vU0g1--\",\n"
        + "  \"cluster_name\" : \"elasticsearch\",\n"
        + "  \"cluster_uuid\" : \"Fnm277ITSNyzsy0UCVFN7g\",\n"
        + "  \"version\" : {\n"
        + "    \"number\" : \"5.0.0\",\n"
        + "    \"build_hash\" : \"253032b\",\n"
        + "    \"build_date\" : \"2016-10-26T04:37:51.531Z\",\n"
        + "    \"build_snapshot\" : false,\n"
        + "    \"lucene_version\" : \"6.2.0\"\n"
        + "  },\n"
        + "  \"tagline\" : \"You Know, for Search\"\n"
        + "}"));

    assertThat(client.getVersion()).isEqualTo("5.0.0");
  }

  @Test
  public void addsPipelineId() throws Exception {
    client.close();
    client = (HttpClient) new HttpClientBuilder(new OkHttpClient())
        .pipeline("zipkin")
        .hosts(asList(es.url("").toString()))
        .buildFactory().create("zipkin-*");

    es.enqueue(new MockResponse());

    CallbackCaptor<Void> callback = new CallbackCaptor<>();
    client.bulkSpanIndexer()
        .add("zipkin-2016-10-01", TestObjects.TRACE.get(0), null)
        .execute(callback);
    callback.get();

    RecordedRequest request = es.takeRequest();
    assertThat(request.getPath())
        .isEqualTo("/_bulk?pipeline=zipkin");
  }
}
