/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
      .autocompleteKeys(List.of("http#host", "http-url", "http.method")).build();
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
