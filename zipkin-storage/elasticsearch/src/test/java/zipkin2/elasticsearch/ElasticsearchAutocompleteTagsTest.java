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
package zipkin2.elasticsearch;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ElasticsearchAutocompleteTagsTest {

  static final AtomicReference<AggregatedHttpRequest> CAPTURED_REQUEST =
    new AtomicReference<>();
  static final AtomicReference<AggregatedHttpResponse> MOCK_RESPONSE =
    new AtomicReference<>();
  static final AggregatedHttpResponse SUCCESS_RESPONSE =
    AggregatedHttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8,
      HttpData.ofUtf8(TestResponses.AUTOCOMPLETE_VALUES));

  @ClassRule public static ServerRule server = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) {
      sb.serviceUnder("/", (ctx, req) -> HttpResponse.from(
        req.aggregate().thenApply(agg -> {
          CAPTURED_REQUEST.set(agg);
          return HttpResponse.of(MOCK_RESPONSE.get());
        })));
    }
  };

  ElasticsearchStorage storage;
  ElasticsearchAutocompleteTags tagStore;

  @Before public void setUp() {
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/")))
      .autocompleteKeys(asList("http#host", "http-url", "http.method")).build();
    tagStore = new ElasticsearchAutocompleteTags(storage);
  }

  @After public void tearDown() throws IOException {
    storage.close();
  }

  @Test public void get_list_of_autocomplete_keys() throws Exception {
    // note: we don't enqueue a request!
    assertThat(tagStore.getKeys().execute())
      .contains("http#host", "http-url", "http.method");
  }

  @Test public void getValues_requestIncludesKeyName() throws Exception {
    MOCK_RESPONSE.set(SUCCESS_RESPONSE);
    tagStore.getValues("http.method").execute();
    assertThat(CAPTURED_REQUEST.get().contentUtf8()).contains("\"tagKey\":\"http.method\"");
  }

  @Test public void getValues() throws Exception {
    MOCK_RESPONSE.set(SUCCESS_RESPONSE);

    assertThat(tagStore.getValues("http.method").execute()).containsOnly("get", "post");
  }
}
