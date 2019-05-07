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

import com.squareup.moshi.JsonReader;
import java.io.IOException;
import okhttp3.Request;
import okio.BufferedSource;
import zipkin2.elasticsearch.internal.client.HttpCall;

import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

/** Returns a version-specific span and dependency index template */
final class VersionSpecificTemplates {
  /**
   * In Zipkin search, we do exact match only (keyword). Norms is about scoring. We don't use that
   * in our API, and disable it to reduce disk storage needed.
   */
  static final String KEYWORD = "{ \"type\": \"keyword\", \"norms\": false }";

  final boolean searchEnabled, strictTraceId;
  final String spanIndexTemplate;
  final String dependencyIndexTemplate;
  final String autocompleteIndexTemplate;

  VersionSpecificTemplates(ElasticsearchStorage es) {
    this.searchEnabled = es.searchEnabled();
    this.strictTraceId = es.strictTraceId();
    this.spanIndexTemplate = spanIndexTemplate()
      .replace("${INDEX}", es.indexNameFormatter().index())
      .replace("${NUMBER_OF_SHARDS}", String.valueOf(es.indexShards()))
      .replace("${NUMBER_OF_REPLICAS}", String.valueOf(es.indexReplicas()))
      .replace("${TRACE_ID_MAPPING}", strictTraceId ? KEYWORD
        // Supporting mixed trace ID length is expensive due to needing a special analyzer and
        // "fielddata" which consumes a lot of heap. Sites should only turn off strict trace ID when
        // in a transition, and keep trace ID length transitions as short time as possible.
        : "{ \"type\": \"text\", \"fielddata\": \"true\", \"analyzer\": \"traceId_analyzer\" }");
    this.dependencyIndexTemplate = DEPENDENCY_INDEX_TEMPLATE
      .replace("${INDEX}", es.indexNameFormatter().index())
      .replace("${NUMBER_OF_SHARDS}", String.valueOf(es.indexShards()))
      .replace("${NUMBER_OF_REPLICAS}", String.valueOf(es.indexReplicas()));
    this.autocompleteIndexTemplate = AUTOCOMPLETE_INDEX_TEMPLATE
      .replace("${INDEX}", es.indexNameFormatter().index())
      .replace("${NUMBER_OF_SHARDS}", String.valueOf(es.indexShards()))
      .replace("${NUMBER_OF_REPLICAS}", String.valueOf(es.indexReplicas()));
  }

  /** Templatized due to version differences. Only fields used in search are declared */
  String spanIndexTemplate() {
    String result = ""
      + "{\n"
      + "  \"index_patterns\": \"${INDEX}${INDEX_TYPE_DELIMITER}span-*\",\n"
      + "  \"settings\": {\n"
      + "    \"index.number_of_shards\": ${NUMBER_OF_SHARDS},\n"
      + "    \"index.number_of_replicas\": ${NUMBER_OF_REPLICAS},\n"
      + "    \"index.requests.cache.enable\": true,\n"
      + "    \"index.mapper.dynamic\": false";

    if (!strictTraceId) {
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
        + "    \"span\": {\n"
        + "      \"_source\": {\"excludes\": [\"_q\"] },\n"
        + "      \"dynamic_templates\": [\n"
        + "        {\n"
        + "          \"strings\": {\n"
        + "            \"mapping\": {\n"
        + "              \"type\": \"keyword\",\"norms\": false,\n"
        + "              \"ignore_above\": 256\n"
        + "            },\n"
        + "            \"match_mapping_type\": \"string\",\n"
        + "            \"match\": \"*\"\n"
        + "          }\n"
        + "        }\n"
        + "      ],\n"
        + "      \"properties\": {\n"
        + "        \"traceId\": ${TRACE_ID_MAPPING},\n"
        + "        \"name\": " + KEYWORD + ",\n"
        + "        \"localEndpoint\": {\n"
        + "          \"type\": \"object\",\n"
        + "          \"dynamic\": false,\n"
        + "          \"properties\": { \"serviceName\": " + KEYWORD + " }\n"
        + "        },\n"
        + "        \"remoteEndpoint\": {\n"
        + "          \"type\": \"object\",\n"
        + "          \"dynamic\": false,\n"
        + "          \"properties\": { \"serviceName\": " + KEYWORD + " }\n"
        + "        },\n"
        + "        \"timestamp_millis\": {\n"
        + "          \"type\":   \"date\",\n"
        + "          \"format\": \"epoch_millis\"\n"
        + "        },\n"
        + "        \"duration\": { \"type\": \"long\" },\n"
        + "        \"annotations\": { \"enabled\": false },\n"
        + "        \"tags\": { \"enabled\": false },\n"
        + "        \"_q\": " + KEYWORD + "\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}");
    }
    return result
      + ("  \"mappings\": {\n"
      + "    \"span\": {\n"
      + "      \"properties\": {\n"
      + "        \"traceId\": ${TRACE_ID_MAPPING},\n"
      + "        \"annotations\": { \"enabled\": false },\n"
      + "        \"tags\": { \"enabled\": false }\n"
      + "      }\n"
      + "    }\n"
      + "  }\n"
      + "}");
  }

  /** Templatized due to version differences. Only fields used in search are declared */
  static final String DEPENDENCY_INDEX_TEMPLATE =
    "{\n"
      + "  \"index_patterns\": \"${INDEX}${INDEX_TYPE_DELIMITER}dependency-*\",\n"
      + "  \"settings\": {\n"
      + "    \"index.number_of_shards\": ${NUMBER_OF_SHARDS},\n"
      + "    \"index.number_of_replicas\": ${NUMBER_OF_REPLICAS},\n"
      + "    \"index.requests.cache.enable\": true,\n"
      + "    \"index.mapper.dynamic\": false\n"
      + "  },\n"
      + "  \"mappings\": {\"dependency\": { \"enabled\": false }}\n"
      + "}";

  // The key filed of a autocompleteKeys is intentionally names as tagKey since it clashes with the
  // BodyConverters KEY
  static final String AUTOCOMPLETE_INDEX_TEMPLATE =
    "{\n"
      + "  \"index_patterns\": \"${INDEX}${INDEX_TYPE_DELIMITER}autocomplete-*\",\n"
      + "  \"settings\": {\n"
      + "    \"index.number_of_shards\": ${NUMBER_OF_SHARDS},\n"
      + "    \"index.number_of_replicas\": ${NUMBER_OF_REPLICAS},\n"
      + "    \"index.requests.cache.enable\": true,\n"
      + "    \"index.mapper.dynamic\": false\n"
      + "  },\n"
      + "  \"mappings\": {\n"
      + "   \"autocomplete\": {\n"
      + "      \"enabled\": true,\n"
      + "      \"properties\": {\n"
      + "        \"tagKey\": " + KEYWORD + ",\n"
      + "        \"tagValue\": " + KEYWORD + "\n"
      + "  }}}\n"
      + "}";

  IndexTemplates get(HttpCall.Factory callFactory) throws IOException {
    float version = getVersion(callFactory);
    if (version < 5.0f || version >= 8.0f) {
      throw new IllegalArgumentException(
        "Elasticsearch versions 5-7.x are supported, was: " + version);
    }
    return IndexTemplates.newBuilder()
      .version(version)
      .indexTypeDelimiter(indexTypeDelimiter(version))
      .span(versionSpecificIndexTemplate(spanIndexTemplate, version))
      .dependency(versionSpecificIndexTemplate(dependencyIndexTemplate, version))
      .autocomplete(versionSpecificIndexTemplate(autocompleteIndexTemplate, version))
      .build();
  }

  /**
   * This returns a delimiter based on what's supported by the Elasticsearch version.
   *
   * <p>Starting in Elasticsearch 7.x, colons are no longer allowed in index names. This logic will
   * make sure the pattern in our index template doesn't use them either.
   *
   * <p>See https://github.com/openzipkin/zipkin/issues/2219
   */
  static char indexTypeDelimiter(float version) {
    return version < 7.0f ? ':' : '-';
  }

  static float getVersion(HttpCall.Factory callFactory) throws IOException {
    Request getNode = new Request.Builder().url(callFactory.baseUrl).tag("get-node").build();
    return callFactory.newCall(getNode, ReadVersionNumber.INSTANCE).execute();
  }

  enum ReadVersionNumber implements HttpCall.BodyConverter<Float> {
    INSTANCE;

    @Override public Float convert(BufferedSource content) throws IOException {
      JsonReader version = enterPath(JsonReader.of(content), "version", "number");
      if (version == null) throw new IllegalStateException(".version.number not in response");
      String versionString = version.nextString();
      return Float.valueOf(versionString.substring(0, 3));
    }
  }

  static String versionSpecificIndexTemplate(String template, float version) {
    String indexTypeDelimiter = Character.toString(indexTypeDelimiter(version));
    template = template.replace("${INDEX_TYPE_DELIMITER}", indexTypeDelimiter);
    if (version < 6.0f) return template.replace("index_patterns", "template");
    // 6.x _all disabled https://www.elastic.co/guide/en/elasticsearch/reference/6.7/breaking-changes-6.0.html#_the_literal__all_literal_meta_field_is_now_disabled_by_default
    // 7.x _default disallowed https://www.elastic.co/guide/en/elasticsearch/reference/current/breaking-changes-7.0.html#_the_literal__default__literal_mapping_is_no_longer_allowed
    if (version < 7.0f) return template;
    // There is no explicit documentation of this setting being removed in 7.0f, but it was.
    return template.replace(",\n    \"index.mapper.dynamic\": false", "");
  }
}

