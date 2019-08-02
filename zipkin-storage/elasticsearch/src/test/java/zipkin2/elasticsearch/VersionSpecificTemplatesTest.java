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
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.elasticsearch.ElasticsearchStorageTest.RESPONSE_UNAUTHORIZED;

public class VersionSpecificTemplatesTest {
  static final AggregatedHttpResponse VERSION_RESPONSE_7 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, ""
      + "{\n"
      + "  \"name\" : \"zipkin-elasticsearch\",\n"
      + "  \"cluster_name\" : \"docker-cluster\",\n"
      + "  \"cluster_uuid\" : \"wByRPgSgTryYl0TZXW4MsA\",\n"
      + "  \"version\" : {\n"
      + "    \"number\" : \"7.0.1\",\n"
      + "    \"build_flavor\" : \"default\",\n"
      + "    \"build_type\" : \"tar\",\n"
      + "    \"build_hash\" : \"e4efcb5\",\n"
      + "    \"build_date\" : \"2019-04-29T12:56:03.145736Z\",\n"
      + "    \"build_snapshot\" : false,\n"
      + "    \"lucene_version\" : \"8.0.0\",\n"
      + "    \"minimum_wire_compatibility_version\" : \"6.7.0\",\n"
      + "    \"minimum_index_compatibility_version\" : \"6.0.0-beta1\"\n"
      + "  },\n"
      + "  \"tagline\" : \"You Know, for Search\"\n"
      + "}");
  static final AggregatedHttpResponse VERSION_RESPONSE_6 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, ""
      + "{\n"
      + "  \"name\" : \"PV-NhJd\",\n"
      + "  \"cluster_name\" : \"CollectorDBCluster\",\n"
      + "  \"cluster_uuid\" : \"UjZaM0fQRC6tkHINCg9y8w\",\n"
      + "  \"version\" : {\n"
      + "    \"number\" : \"6.7.0\",\n"
      + "    \"build_flavor\" : \"oss\",\n"
      + "    \"build_type\" : \"tar\",\n"
      + "    \"build_hash\" : \"8453f77\",\n"
      + "    \"build_date\" : \"2019-03-21T15:32:29.844721Z\",\n"
      + "    \"build_snapshot\" : false,\n"
      + "    \"lucene_version\" : \"7.7.0\",\n"
      + "    \"minimum_wire_compatibility_version\" : \"5.6.0\",\n"
      + "    \"minimum_index_compatibility_version\" : \"5.0.0\"\n"
      + "  },\n"
      + "  \"tagline\" : \"You Know, for Search\"\n"
      + "}");
  static final AggregatedHttpResponse VERSION_RESPONSE_5 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, ""
      + "{\n"
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
      + "}");
  static final AggregatedHttpResponse VERSION_RESPONSE_2 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, ""
      + "{\n"
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
      + "}");

  static final AtomicReference<AggregatedHttpResponse> MOCK_RESPONSE =
    new AtomicReference<>();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @ClassRule public static ServerRule server = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) {
      sb.serviceUnder("/", (ctx, req) -> HttpResponse.of(MOCK_RESPONSE.get()));
    }
  };

  @Before public void setUp() {
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/"))).build();
  }

  @After public void tearDown() {
    storage.close();
  }

  ElasticsearchStorage storage;

  @Test public void wrongContent() throws Exception {
    MOCK_RESPONSE.set(AggregatedHttpResponse.of(
      ResponseHeaders.of(HttpStatus.OK),
      HttpData.ofUtf8("you got mail")));

    thrown.expectMessage(".version.number not found in response: you got mail");

    new VersionSpecificTemplates(storage).get();
  }

  @Test public void unauthorized() throws Exception {
    MOCK_RESPONSE.set(RESPONSE_UNAUTHORIZED);

    thrown.expectMessage("User: anonymous is not authorized to perform: es:ESHttpGet");

    new VersionSpecificTemplates(storage).get();
  }

  /** Unsupported, but we should test that parsing works */
  @Test public void version2_unsupported() throws Exception {
    MOCK_RESPONSE.set(VERSION_RESPONSE_2);

    thrown.expectMessage("Elasticsearch versions 5-7.x are supported, was: 2.4");

    new VersionSpecificTemplates(storage).get();
  }

  @Test public void version5() throws Exception {
    MOCK_RESPONSE.set(VERSION_RESPONSE_5);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.version()).isEqualTo(5.0f);
    assertThat(template.autocomplete())
      .withFailMessage("In v5.x, the index_patterns field was named template")
      .contains("\"template\":");
    assertThat(template.autocomplete())
      .withFailMessage("Until v7.x, we delimited index and type with a colon")
      .contains("\"template\": \"zipkin:autocomplete-*\"");
    assertThat(template.autocomplete())
      .contains("\"index.mapper.dynamic\": false");
  }

  @Test public void version6() throws Exception {
    MOCK_RESPONSE.set(VERSION_RESPONSE_6);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.version()).isEqualTo(6.7f);
    assertThat(template.autocomplete())
      .withFailMessage("Until v7.x, we delimited index and type with a colon")
      .contains("\"index_patterns\": \"zipkin:autocomplete-*\"");
    assertThat(template.autocomplete())
      .contains("\"index.mapper.dynamic\": false");
  }

  @Test public void version6_wrapsPropertiesWithType() throws Exception {
    MOCK_RESPONSE.set(VERSION_RESPONSE_6);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.dependency()).contains(""
      + "  \"mappings\": {\n"
      + "    \"dependency\": {\n"
      + "      \"enabled\": false\n"
      + "    }\n"
      + "  }");

    assertThat(template.autocomplete()).contains(""
      + "  \"mappings\": {\n"
      + "    \"autocomplete\": {\n"
      + "      \"enabled\": true,\n"
      + "      \"properties\": {\n"
      + "        \"tagKey\": { \"type\": \"keyword\", \"norms\": false },\n"
      + "        \"tagValue\": { \"type\": \"keyword\", \"norms\": false }\n"
      + "      }\n"
      + "    }\n"
      + "  }");
  }

  @Test public void version7() throws Exception {
    MOCK_RESPONSE.set(VERSION_RESPONSE_7);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.version()).isEqualTo(7.0f);
    assertThat(template.autocomplete())
      .withFailMessage("Starting at v7.x, we delimit index and type with hyphen")
      .contains("\"index_patterns\": \"zipkin-autocomplete-*\"");
    assertThat(template.autocomplete())
      .withFailMessage("7.x does not support the key index.mapper.dynamic")
      .doesNotContain("\"index.mapper.dynamic\": false");
  }

  @Test public void version7_doesntWrapPropertiesWithType() throws Exception {
    MOCK_RESPONSE.set(VERSION_RESPONSE_7);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.dependency()).contains(""
      + "  \"mappings\": {\n"
      + "    \"enabled\": false\n"
      + "  }");

    assertThat(template.autocomplete()).contains(""
      + "  \"mappings\": {\n"
      + "    \"enabled\": true,\n"
      + "    \"properties\": {\n"
      + "      \"tagKey\": { \"type\": \"keyword\", \"norms\": false },\n"
      + "      \"tagValue\": { \"type\": \"keyword\", \"norms\": false }\n"
      + "    }\n"
      + "  }");
  }

  @Test public void searchEnabled_minimalSpanIndexing_6x() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/")))
      .searchEnabled(false)
      .build();

    MOCK_RESPONSE.set(VERSION_RESPONSE_6);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.span())
      .contains(""
        + "  \"mappings\": {\n"
        + "    \"span\": {\n"
        + "      \"properties\": {\n"
        + "        \"traceId\": { \"type\": \"keyword\", \"norms\": false },\n"
        + "        \"annotations\": { \"enabled\": false },\n"
        + "        \"tags\": { \"enabled\": false }\n"
        + "      }\n"
        + "    }\n"
        + "  }");
  }

  @Test public void searchEnabled_minimalSpanIndexing_7x() throws Exception {
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/")))
      .searchEnabled(false)
      .build();

    MOCK_RESPONSE.set(VERSION_RESPONSE_7);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    // doesn't wrap in a type name
    assertThat(template.span())
      .contains(""
        + "  \"mappings\": {\n"
        + "    \"properties\": {\n"
        + "      \"traceId\": { \"type\": \"keyword\", \"norms\": false },\n"
        + "      \"annotations\": { \"enabled\": false },\n"
        + "      \"tags\": { \"enabled\": false }\n"
        + "    }\n"
        + "  }");
  }

  @Test public void strictTraceId_doesNotIncludeAnalysisSection() throws Exception {
    MOCK_RESPONSE.set(VERSION_RESPONSE_6);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.span()).doesNotContain("analysis");
  }

  @Test public void strictTraceId_false_includesAnalysisForMixedLengthTraceId() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/")))
      .strictTraceId(false)
      .build();

    MOCK_RESPONSE.set(VERSION_RESPONSE_6);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.span()).contains("analysis");
  }
}
