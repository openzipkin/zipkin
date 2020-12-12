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

import static zipkin2.elasticsearch.ElasticsearchVersion.V5_0;
import static zipkin2.elasticsearch.ElasticsearchVersion.V6_0;
import static zipkin2.elasticsearch.ElasticsearchVersion.V7_0;
import static zipkin2.elasticsearch.ElasticsearchVersion.V7_8;
import static zipkin2.elasticsearch.ElasticsearchVersion.V8_0;

/** Returns version-specific index templates */
// TODO: make a main class that spits out the index template using ENV variables for the server,
// a parameter for the version, and a parameter for the index type. Ex.
// java -cp zipkin-storage-elasticsearch.jar zipkin2.elasticsearch.VersionSpecificTemplates 6.7 span
final class VersionSpecificTemplates {
  /** Maximum character length constraint of most names, IP literals and IDs. */
  static final int SHORT_STRING_LENGTH = 256;
  static final String TYPE_AUTOCOMPLETE = "autocomplete";
  static final String TYPE_SPAN = "span";
  static final String TYPE_DEPENDENCY = "dependency";

  /**
   * In Zipkin search, we do exact match only (keyword). Norms is about scoring. We don't use that
   * in our API, and disable it to reduce disk storage needed.
   */
  static final String KEYWORD = "{ \"type\": \"keyword\", \"norms\": false }";

  final String indexPrefix;
  final int indexReplicas, indexShards;
  final boolean searchEnabled, strictTraceId;
  final Integer templatePriority;

  VersionSpecificTemplates(String indexPrefix, int indexReplicas, int indexShards,
    boolean searchEnabled, boolean strictTraceId, Integer templatePriority) {
    this.indexPrefix = indexPrefix;
    this.indexReplicas = indexReplicas;
    this.indexShards = indexShards;
    this.searchEnabled = searchEnabled;
    this.strictTraceId = strictTraceId;
    this.templatePriority = templatePriority;
  }

  String indexPattern(String type, ElasticsearchVersion version) {
    return '"'
      + (version.compareTo(V6_0) < 0 ? "template" : "index_patterns")
      + "\": \""
      + indexPrefix
      + indexTypeDelimiter(version)
      + type
      + "-*"
      + "\"";
  }

  String indexProperties(ElasticsearchVersion version) {
    // 6.x _all disabled https://www.elastic.co/guide/en/elasticsearch/reference/6.7/breaking-changes-6.0.html#_the_literal__all_literal_meta_field_is_now_disabled_by_default
    // 7.x _default disallowed https://www.elastic.co/guide/en/elasticsearch/reference/current/breaking-changes-7.0.html#_the_literal__default__literal_mapping_is_no_longer_allowed
    String result = ""
      + "    \"index.number_of_shards\": " + indexShards + ",\n"
      + "    \"index.number_of_replicas\": " + indexReplicas + ",\n"
      + "    \"index.requests.cache.enable\": true";
    return result + "\n";
  }

  String indexTemplate(ElasticsearchVersion version) {
    if (useComposableTemplate(version)) {
      return "\"template\": {\n";
    }

    return "";
  }

  String indexTemplateClosing(ElasticsearchVersion version) {
    if (useComposableTemplate(version)) {
      return "},\n";
    }

    return "";
  }

  String templatePriority(ElasticsearchVersion version) {
    if (useComposableTemplate(version)) {
      return "\"priority\": " + templatePriority + "\n";
    }

    return "";
  }

  String beginTemplate(String type, ElasticsearchVersion version) {
    return "{\n"
      + "  " + indexPattern(type, version) + ",\n"
      + indexTemplate(version)
      + "  \"settings\": {\n"
      + indexProperties(version);
  }

  String endTemplate(ElasticsearchVersion version) {
    return indexTemplateClosing(version)
      + templatePriority(version)
      + "}";
  }

  /** Templatized due to version differences. Only fields used in search are declared */
  String spanIndexTemplate(ElasticsearchVersion version) {
    String result = beginTemplate(TYPE_SPAN, version);

    String traceIdMapping = KEYWORD;
    if (!strictTraceId) {
      // Supporting mixed trace ID length is expensive due to needing a special analyzer and
      // "fielddata" which consumes a lot of heap. Sites should only turn off strict trace ID when
      // in a transition, and keep trace ID length transitions as short time as possible.
      traceIdMapping =
        "{ \"type\": \"text\", \"fielddata\": \"true\", \"analyzer\": \"traceId_analyzer\" }";
      result += (",\n"
        + "    \"analysis\": {\n"
        + "      \"analyzer\": {\n"
        + "        \"traceId_analyzer\": {\n"
        + "          \"type\": \"custom\",\n"
        + "          \"tokenizer\": \"keyword\",\n"
        + "          \"filter\": \"traceId_filter\"\n"
        + "        }\n"
        + "      },\n"
        + "      \"filter\": {\n"
        + "        \"traceId_filter\": {\n"
        + "          \"type\": \"pattern_capture\",\n"
        + "          \"patterns\": [\"([0-9a-f]{1,16})$\"],\n"
        + "          \"preserve_original\": true\n"
        + "        }\n"
        + "      }\n"
        + "    }\n");
    }

    result += "  },\n";

    if (searchEnabled) {
      return result
        + ("  \"mappings\": {\n"
        + maybeWrap(TYPE_SPAN, version, ""
        + "    \"_source\": {\"excludes\": [\"_q\"] },\n"
        + "    \"dynamic_templates\": [\n"
        + "      {\n"
        + "        \"strings\": {\n"
        + "          \"mapping\": {\n"
        + "            \"type\": \"keyword\",\"norms\": false,"
        + " \"ignore_above\": " + SHORT_STRING_LENGTH + "\n"
        + "          },\n"
        + "          \"match_mapping_type\": \"string\",\n"
        + "          \"match\": \"*\"\n"
        + "        }\n"
        + "      }\n"
        + "    ],\n"
        + "    \"properties\": {\n"
        + "      \"traceId\": " + traceIdMapping + ",\n"
        + "      \"name\": " + KEYWORD + ",\n"
        + "      \"localEndpoint\": {\n"
        + "        \"type\": \"object\",\n"
        + "        \"dynamic\": false,\n"
        + "        \"properties\": { \"serviceName\": " + KEYWORD + " }\n"
        + "      },\n"
        + "      \"remoteEndpoint\": {\n"
        + "        \"type\": \"object\",\n"
        + "        \"dynamic\": false,\n"
        + "        \"properties\": { \"serviceName\": " + KEYWORD + " }\n"
        + "      },\n"
        + "      \"timestamp_millis\": {\n"
        + "        \"type\":   \"date\",\n"
        + "        \"format\": \"epoch_millis\"\n"
        + "      },\n"
        + "      \"duration\": { \"type\": \"long\" },\n"
        + "      \"annotations\": { \"enabled\": false },\n"
        + "      \"tags\": { \"enabled\": false },\n"
        + "      \"_q\": " + KEYWORD + "\n"
        + "    }\n")
        + "  }\n"
        + endTemplate(version));
    }
    return result
      + ("  \"mappings\": {\n"
      + maybeWrap(TYPE_SPAN, version, ""
      + "    \"properties\": {\n"
      + "      \"traceId\": " + traceIdMapping + ",\n"
      + "      \"annotations\": { \"enabled\": false },\n"
      + "      \"tags\": { \"enabled\": false }\n"
      + "    }\n")
      + "  }\n"
      + endTemplate(version));
  }

  /** Templatized due to version differences. Only fields used in search are declared */
  String dependencyTemplate(ElasticsearchVersion version) {
    return beginTemplate(TYPE_DEPENDENCY, version)
      + "  },\n"
      + "  \"mappings\": {\n"
      + maybeWrap(TYPE_DEPENDENCY, version, "    \"enabled\": false\n")
      + "  }\n"
      + endTemplate(version);
  }

  // The key filed of a autocompleteKeys is intentionally names as tagKey since it clashes with the
  // BodyConverters KEY
  String autocompleteTemplate(ElasticsearchVersion version) {
    return beginTemplate(TYPE_AUTOCOMPLETE, version)
      + "  },\n"
      + "  \"mappings\": {\n"
      + maybeWrap(TYPE_AUTOCOMPLETE, version, ""
      + "    \"enabled\": true,\n"
      + "    \"properties\": {\n"
      + "      \"tagKey\": " + KEYWORD + ",\n"
      + "      \"tagValue\": " + KEYWORD + "\n"
      + "    }\n")
      + "  }\n"
      + endTemplate(version);
  }

  IndexTemplates get(ElasticsearchVersion version) {
    if (version.compareTo(V5_0) < 0 || version.compareTo(V8_0) >= 0) {
      throw new IllegalArgumentException(
        "Elasticsearch versions 5-7.x are supported, was: " + version);
    }
    return IndexTemplates.newBuilder()
      .version(version)
      .indexTypeDelimiter(indexTypeDelimiter(version))
      .span(spanIndexTemplate(version))
      .dependency(dependencyTemplate(version))
      .autocomplete(autocompleteTemplate(version))
      .build();
  }

  boolean useComposableTemplate(ElasticsearchVersion version) {
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

  static String maybeWrap(String type, ElasticsearchVersion version, String json) {
    // ES 7.x defaults include_type_name to false https://www.elastic.co/guide/en/elasticsearch/reference/current/breaking-changes-7.0.html#_literal_include_type_name_literal_now_defaults_to_literal_false_literal
    if (version.compareTo(V7_0) >= 0) return json;
    return "    \"" + type + "\": {\n  " + json.replace("\n", "\n  ") + "  }\n";
  }
}

