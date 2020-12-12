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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static zipkin2.elasticsearch.ElasticsearchVersion.V5_0;
import static zipkin2.elasticsearch.ElasticsearchVersion.V7_0;
import static zipkin2.elasticsearch.ElasticsearchVersion.V7_8;

class VersionSpecificTemplatesTest {
  static final ElasticsearchVersion V2_4 = new ElasticsearchVersion(2, 4);
  static final ElasticsearchVersion V6_7 = new ElasticsearchVersion(6, 7);
  static final ElasticsearchVersion V7_9 = new ElasticsearchVersion(7, 9);

  ElasticsearchStorage storage =
    ElasticsearchStorage.newBuilder(() -> mock(WebClient.class)).build();

  /** Unsupported, but we should test that parsing works */
  @Test void version2_unsupported() {
    assertThatThrownBy(() -> storage.versionSpecificTemplates(V2_4))
      .hasMessage("Elasticsearch versions 5-7.x are supported, was: 2.4");
  }

  @Test void version5() {
    IndexTemplates template = storage.versionSpecificTemplates(V5_0);

    assertThat(template.version()).isEqualTo(V5_0);
    assertThat(template.autocomplete())
      .withFailMessage("In v5.x, the index_patterns field was named template")
      .contains("\"template\":");
    assertThat(template.autocomplete())
      .withFailMessage("Until v7.x, we delimited index and type with a colon")
      .contains("\"template\": \"zipkin:autocomplete-*\"");
  }

  @Test void version6() {
    IndexTemplates template = storage.versionSpecificTemplates(V6_7);

    assertThat(template.version()).isEqualTo(V6_7);
    assertThat(template.autocomplete())
      .withFailMessage("Until v7.x, we delimited index and type with a colon")
      .contains("\"index_patterns\": \"zipkin:autocomplete-*\"");
  }

  @Test void version6_wrapsPropertiesWithType() {
    IndexTemplates template = storage.versionSpecificTemplates(V6_7);

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

  @Test void version7() {
    IndexTemplates template = storage.versionSpecificTemplates(V7_0);

    assertThat(template.version()).isEqualTo(V7_0);
    assertThat(template.autocomplete())
      .withFailMessage("Starting at v7.x, we delimit index and type with hyphen")
      .contains("\"index_patterns\": \"zipkin-autocomplete-*\"");
    assertThat(template.autocomplete())
      .withFailMessage("7.x does not support the key index.mapper.dynamic")
      .doesNotContain("\"index.mapper.dynamic\": false");
  }

  @Test void version7_doesntWrapPropertiesWithType() {
    IndexTemplates template = storage.versionSpecificTemplates(V7_0);

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

  @Test void version78_legacy() {
    IndexTemplates template = storage.versionSpecificTemplates(V7_8);

    assertThat(template.version()).isEqualTo(V7_8);
    assertThat(template.autocomplete())
      .withFailMessage("Starting at v7.x, we delimit index and type with hyphen")
      .contains("\"index_patterns\": \"zipkin-autocomplete-*\"");
    assertThat(template.span())
      .doesNotContain("\"template\": {\n")
      .doesNotContain("\"priority\": 0\n");
    assertThat(template.autocomplete())
      .doesNotContain("\"template\": {\n")
      .doesNotContain("\"priority\": 0\n");
    assertThat(template.dependency())
      .doesNotContain("\"template\": {\n")
      .doesNotContain("\"priority\": 0\n");
  }

  @Test void version78_composable() {
    // Set up a new storage with priority
    storage.close();
    storage =
      ElasticsearchStorage.newBuilder(() -> mock(WebClient.class)).templatePriority(0).build();
    IndexTemplates template = storage.versionSpecificTemplates(V7_8);

    assertThat(template.version()).isEqualTo(V7_8);
    assertThat(template.autocomplete())
      .withFailMessage("Starting at v7.x, we delimit index and type with hyphen")
      .contains("\"index_patterns\": \"zipkin-autocomplete-*\"");
    assertThat(template.span())
      .contains("\"template\": {\n")
      .contains("\"priority\": 0\n");
    assertThat(template.autocomplete())
      .contains("\"template\": {\n")
      .contains("\"priority\": 0\n");
    assertThat(template.dependency())
      .contains("\"template\": {\n")
      .contains("\"priority\": 0\n");
  }

  @Test void version79_legacy() {
    IndexTemplates template = storage.versionSpecificTemplates(V7_9);

    assertThat(template.version()).isEqualTo(V7_9);
    assertThat(template.autocomplete())
      .withFailMessage("Starting at v7.x, we delimit index and type with hyphen")
      .contains("\"index_patterns\": \"zipkin-autocomplete-*\"");
    assertThat(template.span())
      .doesNotContain("\"template\": {\n")
      .doesNotContain("\"priority\": 0\n");
    assertThat(template.autocomplete())
      .doesNotContain("\"template\": {\n")
      .doesNotContain("\"priority\": 0\n");
    assertThat(template.dependency())
      .doesNotContain("\"template\": {\n")
      .doesNotContain("\"priority\": 0\n");
  }

  @Test void version79_composable() {
    // Set up a new storage with priority
    storage.close();
    storage =
      ElasticsearchStorage.newBuilder(() -> mock(WebClient.class)).templatePriority(0).build();
    IndexTemplates template = storage.versionSpecificTemplates(V7_9);

    assertThat(template.version()).isEqualTo(V7_9);
    assertThat(template.autocomplete())
      .withFailMessage("Starting at v7.x, we delimit index and type with hyphen")
      .contains("\"index_patterns\": \"zipkin-autocomplete-*\"");
    assertThat(template.span())
      .contains("\"template\": {\n")
      .contains("\"priority\": 0\n");
    assertThat(template.autocomplete())
      .contains("\"template\": {\n")
      .contains("\"priority\": 0\n");
    assertThat(template.dependency())
      .contains("\"template\": {\n")
      .contains("\"priority\": 0\n");
  }

  @Test void searchEnabled_minimalSpanIndexing_6x() {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> mock(WebClient.class))
      .searchEnabled(false)
      .build();

    IndexTemplates template = storage.versionSpecificTemplates(V6_7);

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

  @Test void searchEnabled_minimalSpanIndexing_7x() {
    storage = ElasticsearchStorage.newBuilder(() -> mock(WebClient.class))
      .searchEnabled(false)
      .build();

    IndexTemplates template = storage.versionSpecificTemplates(V7_0);

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

  @Test void strictTraceId_doesNotIncludeAnalysisSection() {
    IndexTemplates template = storage.versionSpecificTemplates(V6_7);

    assertThat(template.span()).doesNotContain("analysis");
  }

  @Test void strictTraceId_false_includesAnalysisForMixedLengthTraceId() {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> mock(WebClient.class))
      .strictTraceId(false)
      .build();

    IndexTemplates template = storage.versionSpecificTemplates(V6_7);

    assertThat(template.span()).contains("analysis");
  }
}
