/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.testing.junit5.server.mock.MockWebServerExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchAutocompleteTagsTest {

  static final AggregatedHttpResponse SUCCESS_RESPONSE =
    AggregatedHttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8,
      HttpData.ofUtf8(TestResponses.AUTOCOMPLETE_VALUES));

  @RegisterExtension static MockWebServerExtension server = new MockWebServerExtension();

  ElasticsearchStorage storage;
  ElasticsearchAutocompleteTags tagStore;

  @BeforeEach void setUp() {
    storage = ElasticsearchStorage.newBuilder(() -> WebClient.of(server.httpUri()))
      .autocompleteKeys(asList("http#host", "http-url", "http.method")).build();
    tagStore = new ElasticsearchAutocompleteTags(storage);
  }

  @AfterEach void tearDown() {
    storage.close();
  }

  @Test void get_list_of_autocomplete_keys() throws Exception {
    // note: we don't enqueue a request!
    assertThat(tagStore.getKeys().execute())
      .contains("http#host", "http-url", "http.method");
  }

  @Test void getValues_requestIncludesKeyName() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);
    tagStore.getValues("http.method").execute();
    assertThat(server.takeRequest().request().contentUtf8()).contains("\"tagKey\":\"http.method\"");
  }

  @Test void getValues() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    assertThat(tagStore.getValues("http.method").execute()).containsOnly("get", "post");
  }
}
