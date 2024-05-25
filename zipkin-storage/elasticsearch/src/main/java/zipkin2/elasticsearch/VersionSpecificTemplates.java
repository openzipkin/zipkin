/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import zipkin2.internal.Nullable;

/** Returns version-specific index templates */
// TODO: make a main class that spits out the index template using ENV variables for the server,
// a parameter for the version, and a parameter for the index type. Ex.
// java -cp zipkin-storage-elasticsearch.jar zipkin2.elasticsearch.VersionSpecificTemplates 6.7 span
abstract class VersionSpecificTemplates<V extends BaseVersion> {
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

  String indexProperties(V version) {
    // 6.x _all disabled https://www.elastic.co/guide/en/elasticsearch/reference/6.7/breaking-changes-6.0.html#_the_literal__all_literal_meta_field_is_now_disabled_by_default
    // 7.x _default disallowed https://www.elastic.co/guide/en/elasticsearch/reference/current/breaking-changes-7.0.html#_the_literal__default__literal_mapping_is_no_longer_allowed
    String result = "    \"index.number_of_shards\": " + indexShards + ",\n"
      + "    \"index.number_of_replicas\": " + indexReplicas + ",\n"
      + "    \"index.requests.cache.enable\": true";
    return result + "\n";
  }

  String indexTemplate(V version) {
    if (useComposableTemplate(version)) {
      return "\"template\": {\n";
    }

    return "";
  }

  String indexTemplateClosing(V version) {
    if (useComposableTemplate(version)) {
      return "},\n";
    }

    return "";
  }

  String templatePriority(V version) {
    if (useComposableTemplate(version)) {
      return "\"priority\": " + templatePriority + "\n";
    }

    return "";
  }

  String beginTemplate(String type, V version) {
    return "{\n"
      + "  " + indexPattern(type, version) + ",\n"
      + indexTemplate(version)
      + "  \"settings\": {\n"
      + indexProperties(version);
  }

  String endTemplate(V version) {
    return indexTemplateClosing(version)
      + templatePriority(version)
      + "}";
  }

  /** Templatized due to version differences. Only fields used in search are declared */
  String spanIndexTemplate(V version) {
    String result = beginTemplate(TYPE_SPAN, version);

    String traceIdMapping = KEYWORD;
    if (!strictTraceId) {
      // Supporting mixed trace ID length is expensive due to needing a special analyzer and
      // "fielddata" which consumes a lot of heap. Sites should only turn off strict trace ID when
      // in a transition, and keep trace ID length transitions as short time as possible.
      traceIdMapping =
        "{ \"type\": \"text\", \"fielddata\": \"true\", \"analyzer\": \"traceId_analyzer\" }";
      result += ("""
        ,
            "analysis": {
              "analyzer": {
                "traceId_analyzer": {
                  "type": "custom",
                  "tokenizer": "keyword",
                  "filter": "traceId_filter"
                }
              },
              "filter": {
                "traceId_filter": {
                  "type": "pattern_capture",
                  "patterns": ["([0-9a-f]{1,16})$"],
                  "preserve_original": true
                }
              }
            }
        """);
    }

    result += "  },\n";

    if (searchEnabled) {
      return result
        + ("  \"mappings\": {\n"
        + maybeWrap(TYPE_SPAN, version, "    \"_source\": {\"excludes\": [\"_q\"] },\n"
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
      + maybeWrap(TYPE_SPAN, version, "    \"properties\": {\n"
      + "      \"traceId\": " + traceIdMapping + ",\n"
      + "      \"annotations\": { \"enabled\": false },\n"
      + "      \"tags\": { \"enabled\": false }\n"
      + "    }\n")
      + "  }\n"
      + endTemplate(version));
  }

  /** Templatized due to version differences. Only fields used in search are declared */
  String dependencyTemplate(V version) {
    return beginTemplate(TYPE_DEPENDENCY, version)
      + "  },\n"
      + "  \"mappings\": {\n"
      + maybeWrap(TYPE_DEPENDENCY, version, "    \"enabled\": false\n")
      + "  }\n"
      + endTemplate(version);
  }

  // The key filed of a autocompleteKeys is intentionally names as tagKey since it clashes with the
  // BodyConverters KEY
  String autocompleteTemplate(V version) {
    return beginTemplate(TYPE_AUTOCOMPLETE, version)
      + "  },\n"
      + "  \"mappings\": {\n"
      + maybeWrap(TYPE_AUTOCOMPLETE, version, "    \"enabled\": true,\n"
      + "    \"properties\": {\n"
      + "      \"tagKey\": " + KEYWORD + ",\n"
      + "      \"tagValue\": " + KEYWORD + "\n"
      + "    }\n")
      + "  }\n"
      + endTemplate(version);
  }

  /**
   * Returns index pattern
   * @param type type 
   * @param version distribution version
   * @return index pattern
   */
  abstract String indexPattern(String type, V version);

  /**
   * Returns index templates
   * @param version distribution version
   * @return index templates
   */
  abstract IndexTemplates get(V version);

  /**
   * Should composable templates be used or not
   * @param version distribution version
   * @return {@code true} if composable templates should be used,
   * {@code false} otherwise
   */
  abstract boolean useComposableTemplate(V version);

  /**
   * Wraps the JSON payload if needed
   * @param type type
   * @param version distribution version
   * @param json JSON payload
   * @return wrapped JSON payload if needed
   */
  abstract String maybeWrap(String type, V version, String json);
 
  /**
   * Returns distribution specific templates (index templates URL, index 
   * type delimiter, {@link IndexTemplates});
   */
  abstract static class DistributionSpecificTemplates {
    /**
     * Returns distribution specific index templates URL
     * @param indexPrefix index prefix
     * @param type type
     * @param templatePriority index template priority
     * @return index templates URL 
     */
    abstract String indexTemplatesUrl(String indexPrefix, String type, @Nullable Integer templatePriority); 

    /**
     * Returns distribution specific index type delimiter
     * @return index type delimiter
     */
    abstract char indexTypeDelimiter();

    /**
     * Returns distribution specific index templates
     * @param indexPrefix index prefix
     * @param indexReplicas number of replicas
     * @param indexShards number of shards
     * @param searchEnabled search is enabled or disabled
     * @param strictTraceId strict trace ID
     * @param templatePriority index template priority
     * @return index templates
     */
    abstract IndexTemplates get(String indexPrefix, int indexReplicas, int indexShards,
      boolean searchEnabled, boolean strictTraceId, Integer templatePriority);
  }

  /**
   * Creates a new {@link DistributionSpecificTemplates} instance based on the distribution
   * @param version distribution version
   * @return {@link OpensearchSpecificTemplates} or {@link ElasticsearchSpecificTemplates} instance
   */
  static DistributionSpecificTemplates forVersion(BaseVersion version) {
    if (version instanceof ElasticsearchVersion) {
      return new ElasticsearchSpecificTemplates.DistributionTemplate((ElasticsearchVersion) version);
    } else if (version instanceof OpensearchVersion) {
      return new OpensearchSpecificTemplates.DistributionTemplate((OpensearchVersion) version);
    } else {
      throw new IllegalArgumentException("The distribution version is not supported: " + version);
    }
  }
}
