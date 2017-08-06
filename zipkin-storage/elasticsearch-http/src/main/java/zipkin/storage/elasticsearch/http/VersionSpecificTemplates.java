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
package zipkin.storage.elasticsearch.http;

import com.squareup.moshi.JsonReader;
import java.util.logging.Logger;
import okhttp3.Request;
import zipkin.storage.elasticsearch.http.internal.client.HttpCall;

import static zipkin.moshi.JsonReaders.enterPath;
import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanStore.DEPENDENCY;
import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanStore.SPAN;

/** Returns a version-specific span and dependency index template */
final class VersionSpecificTemplates {
  static final Logger LOG = Logger.getLogger(VersionSpecificTemplates.class.getName());

  // TODO: remove when we stop writing span1 format
  final String legacyIndexTemplate;
  final String spanIndexTemplate;
  final String dependencyIndexTemplate;

  VersionSpecificTemplates(ElasticsearchHttpStorage es) {
    this.legacyIndexTemplate = LEGACY_INDEX_TEMPLATE
      .replace("${__INDEX__}", es.indexNameFormatter().index())
      .replace("${__NUMBER_OF_SHARDS__}", String.valueOf(es.indexShards()))
      .replace("${__NUMBER_OF_REPLICAS__}", String.valueOf(es.indexReplicas()))
      .replace("${__TRACE_ID_MAPPING__}", es.strictTraceId()
        ? "{ KEYWORD }" : "{ \"type\": \"STRING\", \"analyzer\": \"traceId_analyzer\" }");
    this.spanIndexTemplate = SPAN_INDEX_TEMPLATE
      .replace("${__INDEX__}", es.indexNameFormatter().index())
      .replace("${__NUMBER_OF_SHARDS__}", String.valueOf(es.indexShards()))
      .replace("${__NUMBER_OF_REPLICAS__}", String.valueOf(es.indexReplicas()))
      .replace("${__TRACE_ID_MAPPING__}", es.strictTraceId()
        ? "{ KEYWORD }" : "{ \"type\": \"STRING\", \"analyzer\": \"traceId_analyzer\" }");
    this.dependencyIndexTemplate = DEPENDENCY_INDEX_TEMPLATE
      .replace("${__INDEX__}", es.indexNameFormatter().index())
      .replace("${__NUMBER_OF_SHARDS__}", String.valueOf(es.indexShards()))
      .replace("${__NUMBER_OF_REPLICAS__}", String.valueOf(es.indexReplicas()));
  }

  /** Templatized due to version differences. Only fields used in search are declared */
  static final String LEGACY_INDEX_TEMPLATE = "{\n"
    + "  \"template\": \"${__INDEX__}-*\",\n"
    + "  \"settings\": {\n"
    + "    \"index.number_of_shards\": ${__NUMBER_OF_SHARDS__},\n"
    + "    \"index.number_of_replicas\": ${__NUMBER_OF_REPLICAS__},\n"
    + "    \"index.requests.cache.enable\": true,\n"
    + "    \"index.mapper.dynamic\": false,\n"
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
    + "    }\n"
    + "  },\n"
    + "  \"mappings\": {\n"
    + "    \"_default_\": {\n"
    + "      \"_all\": {\n"
    + "        \"enabled\": false\n"
    + "      }\n"
    + "    },\n"
    + "    \"span\": {\n"
    + "      \"properties\": {\n"
    + "        \"traceId\": ${__TRACE_ID_MAPPING__},\n"
    + "        \"name\": { KEYWORD },\n"
    + "        \"timestamp_millis\": {\n"
    + "          \"type\":   \"date\",\n"
    + "          \"format\": \"epoch_millis\"\n"
    + "        },\n"
    + "        \"duration\": { \"type\": \"long\" },\n"
    + "        \"annotations\": {\n"
    + "          \"type\": \"nested\",\n"
    + "          \"dynamic\": false,\n"
    + "          \"properties\": {\n"
    + "            \"value\": { KEYWORD },\n"
    + "            \"endpoint\": {\n"
    + "              \"type\": \"object\",\n"
    + "              \"dynamic\": false,\n"
    + "              \"properties\": { \"serviceName\": { KEYWORD } }\n"
    + "            }\n"
    + "          }\n"
    + "        },\n"
    + "        \"binaryAnnotations\": {\n"
    + "          \"type\": \"nested\",\n"
    + "          \"dynamic\": false,\n"
    + "          \"properties\": {\n"
    + "            \"key\": { KEYWORD },\n"
    + "            \"value\": { KEYWORD },\n"
    + "            \"endpoint\": {\n"
    + "              \"type\": \"object\",\n"
    + "              \"dynamic\": false,\n"
    + "              \"properties\": { \"serviceName\": { KEYWORD } }\n"
    + "            }\n"
    + "          }\n"
    + "        }\n"
    + "      }\n"
    + "    },\n"
    + "    \"dependencylink\": { \"enabled\": false },\n"
    + "    \"servicespan\": {\n"
    + "      \"properties\": {\n"
    + "        \"serviceName\": { KEYWORD },\n"
    + "        \"spanName\": { KEYWORD }\n"
    + "      }\n"
    + "    }\n"
    + "  }\n"
    + "}";

  /** Templatized due to version differences. Only fields used in search are declared */
  static final String SPAN_INDEX_TEMPLATE = "{\n"
    + "  \"TEMPLATE\": \"${__INDEX__}:" + SPAN + "-*\",\n"
    + "  \"settings\": {\n"
    + "    \"index.number_of_shards\": ${__NUMBER_OF_SHARDS__},\n"
    + "    \"index.number_of_replicas\": ${__NUMBER_OF_REPLICAS__},\n"
    + "    \"index.requests.cache.enable\": true,\n"
    + "    \"index.mapper.dynamic\": false,\n"
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
    + "    }\n"
    + "  },\n"
    + "  \"mappings\": {\n"
    + "    \"_default_\": {\n"
    + "      \"dynamic_templates\": [\n"
    + "        {\n"
    + "          \"strings\": {\n"
    + "            \"mapping\": {\n"
    + "              KEYWORD,\n"
    + "              \"ignore_above\": 256\n"
    + "            },\n"
    + "            \"match_mapping_type\": \"string\",\n"
    + "            \"match\": \"*\"\n"
    + "          }\n"
    + "        }\n"
    + "      ]\n"
    + "    },\n"
    + "    \"" + SPAN + "\": {\n"
    + "      \"properties\": {\n"
    + "        \"traceId\": ${__TRACE_ID_MAPPING__},\n"
    + "        \"name\": { KEYWORD },\n"
    + "        \"localEndpoint\": {\n"
    + "          \"type\": \"object\",\n"
    + "          \"dynamic\": false,\n"
    + "          \"properties\": { \"serviceName\": { KEYWORD } }\n"
    + "        },\n"
    + "        \"remoteEndpoint\": {\n"
    + "          \"type\": \"object\",\n"
    + "          \"dynamic\": false,\n"
    + "          \"properties\": { \"serviceName\": { KEYWORD } }\n"
    + "        },\n"
    + "        \"timestamp_millis\": {\n"
    + "          \"type\":   \"date\",\n"
    + "          \"format\": \"epoch_millis\"\n"
    + "        },\n"
    + "        \"duration\": { \"type\": \"long\" },\n"
    + "        \"annotations\": {\n"
    + "          \"type\": \"object\",\n"
    + "          \"dynamic\": false,\n"
    + "          \"properties\": {\n"
    + "            \"value\": { KEYWORD }\n"
    + "          }\n"
    + "        },\n"
    + "        \"tags\": {\n"
    + "          \"type\": \"object\",\n"
    + "          \"dynamic\": true\n"
    + "        }\n"
    + "      }\n"
    + "    }\n"
    + "  }\n"
    + "}";

  /** Templatized due to version differences. Only fields used in search are declared */
  static final String DEPENDENCY_INDEX_TEMPLATE = "{\n"
    + "  \"TEMPLATE\": \"${__INDEX__}:" + DEPENDENCY + "-*\",\n"
    + "  \"settings\": {\n"
    + "    \"index.number_of_shards\": ${__NUMBER_OF_SHARDS__},\n"
    + "    \"index.number_of_replicas\": ${__NUMBER_OF_REPLICAS__},\n"
    + "    \"index.requests.cache.enable\": true,\n"
    + "    \"index.mapper.dynamic\": false\n"
    + "  },\n"
    + "  \"mappings\": {\"" + DEPENDENCY + "\": { \"enabled\": false }}\n"
    + "}";

  IndexTemplates get(HttpCall.Factory callFactory) {
    float version = getVersion(callFactory);
    return IndexTemplates.builder()
      .version(version)
      .legacy(version < 6 ? versionSpecificLegacyTemplate(version) : null)
      .span(version > 2.4 ? versionSpecificSpanIndexTemplate(version) : null)
      .dependency(version > 2.4 ? versionSpecificDependencyLinkIndexTemplate(version) : null)
      .build();
  }

  static float getVersion(HttpCall.Factory callFactory) {
    Request getNode = new Request.Builder().url(callFactory.baseUrl).tag("get-node").build();
    return callFactory.execute(getNode, b -> {
      JsonReader version = enterPath(JsonReader.of(b), "version", "number");
      if (version == null) throw new IllegalStateException(".version.number not in response");
      String versionString = version.nextString();
      float result = Float.valueOf(versionString.substring(0, 3));
      if (result < 2.4) {
        LOG.warning("Please upgrade to Elasticsearch 2.4 or later. version=" + versionString);
      }
      return result;
    });
  }

  private String versionSpecificLegacyTemplate(float version) {
    if (version >= 2 && version < 3) {
      return legacyIndexTemplate
        .replace("STRING", "string")
        .replace("KEYWORD",
          "\"type\": \"string\", \"ignore_above\": 256, \"norms\": {\"enabled\": false }, \"index\": \"not_analyzed\"");
    } else if (version >= 5 && version < 6) {
      return legacyIndexTemplate
        .replace("STRING", "text")
        .replace("KEYWORD",
          "\"type\": \"keyword\", \"ignore_above\": 256, \"norms\": false")
        .replace("\"analyzer\": \"traceId_analyzer\" }",
          "\"fielddata\": \"true\", \"analyzer\": \"traceId_analyzer\" }");
    } else {
      throw new IllegalStateException(
        "Elasticsearch 2.x and 5.x support multi-type indexes, was: " + version);
    }
  }

  private String versionSpecificSpanIndexTemplate(float version) {
    if (version >= 2.4 && version < 3) {
      return spanIndexTemplate
        .replace("TEMPLATE", "template")
        .replace("STRING", "string")
        .replace("KEYWORD",
          "\"type\": \"string\", \"norms\": {\"enabled\": false }, \"index\": \"not_analyzed\"");
    } else if (version >= 5) {
      return spanIndexTemplate
        .replace("TEMPLATE", version >= 6 ? "index_patterns" : "template")
        .replace("STRING", "text")
        .replace("KEYWORD",
          "\"type\": \"keyword\", \"norms\": false")
        .replace("\"analyzer\": \"traceId_analyzer\" }",
          "\"fielddata\": \"true\", \"analyzer\": \"traceId_analyzer\" }");
    } else {
      throw new IllegalStateException(
        "Elasticsearch 2.4+, 5.x and 6.x allow dots in field names, was: " + version);
    }
  }

  private String versionSpecificDependencyLinkIndexTemplate(float version) {
    return dependencyIndexTemplate.replace("TEMPLATE",
      version >= 6 ? "index_patterns" : "template");
  }
}
