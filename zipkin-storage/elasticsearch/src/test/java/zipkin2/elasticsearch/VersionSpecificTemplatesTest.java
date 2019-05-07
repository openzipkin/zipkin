/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.elasticsearch;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class VersionSpecificTemplatesTest {
  static final MockResponse VERSION_RESPONSE_7 = new MockResponse().setBody(""
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
  static final MockResponse VERSION_RESPONSE_6 = new MockResponse().setBody(""
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
  static final MockResponse VERSION_RESPONSE_5 = new MockResponse().setBody(""
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
  static final MockResponse VERSION_RESPONSE_2 = new MockResponse().setBody(""
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

  @Rule public ExpectedException thrown = ExpectedException.none();
  @Rule public MockWebServer es = new MockWebServer();

  ElasticsearchStorage storage =
    ElasticsearchStorage.newBuilder().hosts(asList(es.url("").toString())).build();

  @After public void close() {
    storage.close();
  }

  /** Unsupported, but we should test that parsing works */
  @Test public void version2_unsupported() throws Exception {
    es.enqueue(VERSION_RESPONSE_2);

    thrown.expectMessage("Elasticsearch versions 5-7.x are supported, was: 2.4");

    new VersionSpecificTemplates(storage).get();
  }

  @Test public void version5() throws Exception {
    es.enqueue(VERSION_RESPONSE_5);

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
    es.enqueue(VERSION_RESPONSE_6);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.version()).isEqualTo(6.7f);
    assertThat(template.autocomplete())
      .withFailMessage("Until v7.x, we delimited index and type with a colon")
      .contains("\"index_patterns\": \"zipkin:autocomplete-*\"");
    assertThat(template.autocomplete())
      .contains("\"index.mapper.dynamic\": false");
  }

  @Test public void version6_wrapsPropertiesWithType() throws Exception {
    es.enqueue(VERSION_RESPONSE_6);

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
    es.enqueue(VERSION_RESPONSE_7);

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
    es.enqueue(VERSION_RESPONSE_7);

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
    storage = ElasticsearchStorage.newBuilder().hosts(storage.hostsSupplier().get())
      .searchEnabled(false)
      .build();

    es.enqueue(VERSION_RESPONSE_6);

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
    storage = ElasticsearchStorage.newBuilder().hosts(storage.hostsSupplier().get())
      .searchEnabled(false)
      .build();

    es.enqueue(VERSION_RESPONSE_7);

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
    es.enqueue(VERSION_RESPONSE_6);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.span()).doesNotContain("analysis");
  }

  @Test public void strictTraceId_false_includesAnalysisForMixedLengthTraceId() throws Exception {
    storage = ElasticsearchStorage.newBuilder().hosts(storage.hostsSupplier().get())
      .strictTraceId(false)
      .build();

    es.enqueue(VERSION_RESPONSE_6);

    IndexTemplates template = new VersionSpecificTemplates(storage).get();

    assertThat(template.span()).contains("analysis");
  }
}
