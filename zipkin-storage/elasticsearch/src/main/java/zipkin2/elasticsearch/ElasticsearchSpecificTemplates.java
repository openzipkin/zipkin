/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package zipkin2.elasticsearch;

import static zipkin2.elasticsearch.ElasticsearchVersion.V5_0;
import static zipkin2.elasticsearch.ElasticsearchVersion.V6_0;
import static zipkin2.elasticsearch.ElasticsearchVersion.V6_7;
import static zipkin2.elasticsearch.ElasticsearchVersion.V7_0;
import static zipkin2.elasticsearch.ElasticsearchVersion.V7_8;
import static zipkin2.elasticsearch.ElasticsearchVersion.V9_0;

import zipkin2.internal.Nullable;

final class ElasticsearchSpecificTemplates extends VersionSpecificTemplates<ElasticsearchVersion> {
  static class DistributionTemplate extends DistributionSpecificTemplates {
    private final ElasticsearchVersion version;

    DistributionTemplate(ElasticsearchVersion version) {
        this.version = version;
    }
    
    @Override String indexTemplatesUrl(String indexPrefix, String type, @Nullable Integer templatePriority) {
      if (version.compareTo(V7_8) >= 0 && templatePriority != null) {
        return "/_index_template/" + indexPrefix + type + "_template";
      }
      if (version.compareTo(V6_7) >= 0 && version.compareTo(V7_0) < 0) {
        // because deprecation warning on 6 to prepare for 7:
        //
        // [types removal] The parameter include_type_name should be explicitly specified in get
        // template requests to prepare for 7.0. In 7.0 include_type_name will default to 'false',
        // which means responses will omit the type name in mapping definitions.
        //
        // The parameter include_type_name was added in 6.7. Using this with ES older than
        // 6.7 will result in unrecognized parameter: [include_type_name].
        return "/_template/" + indexPrefix + type + "_template?include_type_name=true";
      }

      return "/_template/" + indexPrefix + type + "_template";
    }

    @Override char indexTypeDelimiter() {
      return ElasticsearchSpecificTemplates.indexTypeDelimiter(version);
    }

    @Override
    IndexTemplates get(String indexPrefix, int indexReplicas, int indexShards,
      boolean searchEnabled, boolean strictTraceId, Integer templatePriority) {
      return new ElasticsearchSpecificTemplates(indexPrefix, indexReplicas, indexShards, 
        searchEnabled, strictTraceId, templatePriority).get(version);
    }
  }

  ElasticsearchSpecificTemplates(String indexPrefix, int indexReplicas, int indexShards,
    boolean searchEnabled, boolean strictTraceId, Integer templatePriority) {
    super(indexPrefix, indexReplicas,indexShards, searchEnabled, strictTraceId, templatePriority); 
  }

  @Override String indexPattern(String type, ElasticsearchVersion version) {
    return '"'
      + (version.compareTo(V6_0) < 0 ? "template" : "index_patterns")
      + "\": \""
      + indexPrefix
      + indexTypeDelimiter(version)
      + type
      + "-*"
      + "\"";
  }
  
  @Override boolean useComposableTemplate(ElasticsearchVersion version) {
    return (version.compareTo(V7_8) >= 0 && templatePriority != null);
  }

  /**
   * This returns a delimiter based on what's supported by the Elasticsearch version.
   *
   * <p>Starting in Elasticsearch 7.x, colons are no longer allowed in index names. This logic will
   * make sure the pattern in our index template doesn't use them either.
   *
   * <p>See https://github.com/openzipkin/zipkin/issues/2219
   */
  static char indexTypeDelimiter(ElasticsearchVersion version) {
    return version.compareTo(V7_0) < 0 ? ':' : '-';
  }

  @Override String maybeWrap(String type, ElasticsearchVersion version, String json) {
    // ES 7.x defaults include_type_name to false https://www.elastic.co/guide/en/elasticsearch/reference/current/breaking-changes-7.0.html#_literal_include_type_name_literal_now_defaults_to_literal_false_literal
    if (version.compareTo(V7_0) >= 0) return json;
    return "    \"" + type + "\": {\n  " + json.replace("\n", "\n  ") + "  }\n";
  }

  @Override IndexTemplates get(ElasticsearchVersion version) {
    if (version.compareTo(V5_0) < 0 || version.compareTo(V9_0) >= 0) {
      throw new IllegalArgumentException(
        "Elasticsearch versions 5-8.x are supported, was: " + version);
    }
    return IndexTemplates.newBuilder()
      .version(version)
      .indexTypeDelimiter(indexTypeDelimiter(version))
      .span(spanIndexTemplate(version))
      .dependency(dependencyTemplate(version))
      .autocomplete(autocompleteTemplate(version))
      .build();
  }
}
