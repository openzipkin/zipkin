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
package zipkin.autoconfigure.storage.elasticsearch.aws;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class ElasticsearchDomainEndpointTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Rule public MockWebServer es = new MockWebServer();

  ElasticsearchDomainEndpoint client = new ElasticsearchDomainEndpoint(
    new OkHttpClient(),
    es.url(""),
    "zipkin53"
  );

  @Test public void publicUrl() throws Exception {
    es.enqueue(new MockResponse().setBody("{\n"
      + "  \"DomainStatus\": {\n"
      + "    \"Endpoint\": \"search-zipkin53-mhdyquzbwwzwvln6phfzr3lldi.ap-southeast-1.es.amazonaws.com\",\n"
      + "    \"Endpoints\": null\n"
      + "  }\n"
      + "}"));

    assertThat(client.get())
      .containsExactly(
        "https://search-zipkin53-mhdyquzbwwzwvln6phfzr3lldi.ap-southeast-1.es.amazonaws.com");
  }

  /** Not quite sure why, but some have reported receiving no URLs at all */
  @Test public void noUrl() throws Exception {
    // simplified.. URL is usually the only thing actually missing
    String body = "{\"DomainStatus\": {}}";
    es.enqueue(new MockResponse().setBody(body));

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("DomainStatus.Endpoint wasn't present in response: " + body);

    client.get();
  }

  /** Not quite sure why, but some have reported receiving no URLs at all */
  @Test public void unauthorizedNoMessage() throws Exception {
    es.enqueue(new MockResponse().setResponseCode(403));

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("/2015-01-01/es/domain/zipkin53 failed with status 403");

    client.get();
  }
}
