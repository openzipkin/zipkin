/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import com.linecorp.armeria.client.WebClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OpensearchSpecificTemplatesTest {
  static final OpensearchVersion V0_3 = new OpensearchVersion(0, 3);
  static final OpensearchVersion V1_3 = new OpensearchVersion(1, 3);
  static final OpensearchVersion V2_0 = new OpensearchVersion(2, 0);

  ElasticsearchStorage storage =
    ElasticsearchStorage.newBuilder(() -> mock(WebClient.class)).build();

  /** Unsupported, but we should test that parsing works */
  @Test void version_unsupported() {
    assertThatThrownBy(() -> storage.versionSpecificTemplates(V0_3))
      .hasMessage("OpenSearch versions 1-3.x are supported, was: 0.3");
  }

  @Test void version2() {
    IndexTemplates template = storage.versionSpecificTemplates(V2_0);

    assertThat(template.version()).isEqualTo(V2_0);
    assertThat(template.autocomplete())
      .withFailMessage("Starting at v7.x, we delimit index and type with hyphen")
      .contains("\"index_patterns\": \"zipkin-autocomplete-*\"");
    assertThat(template.autocomplete())
      .withFailMessage("7.x does not support the key index.mapper.dynamic")
      .doesNotContain("\"index.mapper.dynamic\": false");
  }

  @Test void version2_doesntWrapPropertiesWithType() {
    IndexTemplates template = storage.versionSpecificTemplates(V2_0);

    assertThat(template.dependency()).contains("""
        "mappings": {
          "enabled": false
        }\
      """);

    assertThat(template.autocomplete()).contains("""
        "mappings": {
          "enabled": true,
          "properties": {
            "tagKey": { "type": "keyword", "norms": false },
            "tagValue": { "type": "keyword", "norms": false }
          }
        }\
      """);
  }

  @Test void searchEnabled_minimalSpanIndexing_1x() {
    storage = ElasticsearchStorage.newBuilder(() -> mock(WebClient.class))
      .searchEnabled(false)
      .build();

    IndexTemplates template = storage.versionSpecificTemplates(V1_3);

    // doesn't wrap in a type name
    assertThat(template.span())
      .contains("""
          "mappings": {
            "properties": {
              "traceId": { "type": "keyword", "norms": false },
              "annotations": { "enabled": false },
              "tags": { "enabled": false }
            }
          }\
        """);
  }

  @Test void strictTraceId_doesNotIncludeAnalysisSection() {
    IndexTemplates template = storage.versionSpecificTemplates(V1_3);

    assertThat(template.span()).doesNotContain("analysis");
  }

  @Test void strictTraceId_false_includesAnalysisForMixedLengthTraceId() {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> mock(WebClient.class))
      .strictTraceId(false)
      .build();

    IndexTemplates template = storage.versionSpecificTemplates(V1_3);

    assertThat(template.span()).contains("analysis");
  }

  @Test void indexTemplatesUrl_1x() {
    assertThat(VersionSpecificTemplates.forVersion(V1_3).indexTemplatesUrl("idx", "_doc", null))
      .isEqualTo("/_template/idx_doc_template");
  }

  @Test void indexTemplatesUrl_1x_withPriority() {
    assertThat(VersionSpecificTemplates.forVersion(V1_3).indexTemplatesUrl("idx", "_doc", 1))
      .isEqualTo("/_index_template/idx_doc_template");
  }

  @Test void indexTemplatesUrl_2x() {
    assertThat(VersionSpecificTemplates.forVersion(V2_0).indexTemplatesUrl("idx", "_doc", null))
      .isEqualTo("/_template/idx_doc_template");
  }

  @Test void indexTemplatesUrl_2x_withPriority() {
    assertThat(VersionSpecificTemplates.forVersion(V2_0).indexTemplatesUrl("idx", "_doc", 1))
      .isEqualTo("/_index_template/idx_doc_template");
  }
}
