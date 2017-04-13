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
import okhttp3.Request;
import zipkin.storage.elasticsearch.http.internal.client.HttpCall;

import static zipkin.moshi.JsonReaders.enterPath;

/** Ensures the index template exists and saves off the version */
final class VersionSpecificTemplate {
  final String indexTemplate;

  VersionSpecificTemplate(ElasticsearchHttpStorage es) {
    this.indexTemplate = INDEX_TEMPLATE
        .replace("${__INDEX__}", es.indexNameFormatter().index())
        .replace("${__NUMBER_OF_SHARDS__}", String.valueOf(es.indexShards()))
        .replace("${__NUMBER_OF_REPLICAS__}", String.valueOf(es.indexReplicas()))
        .replace("${__TRACE_ID_MAPPING__}", es.strictTraceId()
            ? "{ KEYWORD }" : "{ \"type\": \"string\", \"analyzer\": \"traceId_analyzer\" }");
  }

  /** Templatized due to version differences. Only fields used in search are declared */
  static final String INDEX_TEMPLATE = "{\n"
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
      + "    \"" + ElasticsearchHttpSpanStore.SPAN + "\": {\n"
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
      + "    \"" + ElasticsearchHttpSpanStore.DEPENDENCY_LINK + "\": { \"enabled\": false },\n"
      + "    \"" + ElasticsearchHttpSpanStore.SERVICE_SPAN + "\": {\n"
      + "      \"properties\": {\n"
      + "        \"serviceName\": { KEYWORD },\n"
      + "        \"spanName\": { KEYWORD }\n"
      + "      }\n"
      + "    }\n"
      + "  }\n"
      + "}";

  /** Returns a version-specific index template */
  String get(HttpCall.Factory callFactory) {
    String version = getVersion(callFactory);
    return versionSpecificTemplate(version);
  }

  static String getVersion(HttpCall.Factory callFactory) {
    Request getNode = new Request.Builder().url(callFactory.baseUrl).tag("get-node").build();

    return callFactory.execute(getNode, b -> {
      JsonReader version = enterPath(JsonReader.of(b), "version", "number");
      if (version == null) throw new IllegalStateException(".version.number not in response");
      return version.nextString();
    });
  }

  private String versionSpecificTemplate(String version) {
    if (version.startsWith("2")) {
      return indexTemplate
          .replace("KEYWORD",
              "\"type\": \"string\", \"ignore_above\": 256, \"norms\": {\"enabled\": false }, \"index\": \"not_analyzed\"");
    } else if (version.startsWith("5")) {
      return indexTemplate
          .replace("KEYWORD",
              "\"type\": \"keyword\", \"ignore_above\": 256, \"norms\": false")
          .replace("\"analyzer\": \"traceId_analyzer\" }",
              "\"fielddata\": \"true\", \"analyzer\": \"traceId_analyzer\" }");
    } else {
      throw new IllegalStateException("Elasticsearch 2.x and 5.x are supported, was: " + version);
    }
  }
}
