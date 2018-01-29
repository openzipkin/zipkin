/**
 * Copyright 2015-2018 The OpenZipkin Authors
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

import com.squareup.moshi.JsonReader;
import java.io.IOException;
import java.util.logging.Logger;
import okhttp3.Request;
import okio.BufferedSource;
import zipkin2.elasticsearch.internal.client.HttpCall;

import static zipkin2.elasticsearch.ElasticsearchSpanStore.DEPENDENCY;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.SPAN;
import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

/** Returns a version-specific span and dependency index template */
final class VersionSpecificTemplates {
  static final Logger LOG = Logger.getLogger(VersionSpecificTemplates.class.getName());

  final boolean searchEnabled;
  final String spanIndexTemplate;
  final String dependencyIndexTemplate;

  VersionSpecificTemplates(ElasticsearchStorage es) {
    this.searchEnabled = es.searchEnabled();
    this.spanIndexTemplate = spanIndexTemplate()
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
  String spanIndexTemplate() {
    String result = "{\n"
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
      + "  },\n";
    if (searchEnabled) {
      return result + ("  \"mappings\": {\n"
        + "    \"_default_\": {\n"
        + "      DISABLE_ALL" // don't concat all fields into big string
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
        + "      \"_source\": {\"excludes\": [\"_q\"] },\n"
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
        + "        \"annotations\": { \"enabled\": false },\n"
        + "        \"tags\": { \"enabled\": false },\n"
        + "        \"_q\": { KEYWORD }\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}");
    }
    return result + ("  \"mappings\": {\n"
      + "    \"_default_\": { DISABLE_ALL },\n"
      + "    \"" + SPAN + "\": {\n"
      + "      \"properties\": {\n"
      + "        \"traceId\": ${__TRACE_ID_MAPPING__},\n"
      + "        \"annotations\": { \"enabled\": false },\n"
      + "        \"tags\": { \"enabled\": false }\n"
      + "      }\n"
      + "    }\n"
      + "  }\n"
      + "}");
  }

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

  IndexTemplates get(HttpCall.Factory callFactory) throws IOException {
    float version = getVersion(callFactory);
    return IndexTemplates.newBuilder()
      .version(version)
      .span(versionSpecificSpanIndexTemplate(version))
      .dependency(versionSpecificDependencyLinkIndexTemplate(version))
      .build();
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
      float result = Float.valueOf(versionString.substring(0, 3));
      if (result < 2) {
        LOG.warning("Please upgrade to Elasticsearch 2 or later. version=" + versionString);
      }
      return result;
    }

    @Override public String toString(){
      return "GetVersion";
    }
  }

  private String versionSpecificSpanIndexTemplate(float version) {
    if (version >= 2 && version < 3) {
      return spanIndexTemplate
        .replace("TEMPLATE", "template")
        .replace("STRING", "string")
        .replace("DISABLE_ALL", "\"_all\": {\"enabled\": false}" + (searchEnabled ? ",\n" : ""))
        .replace("KEYWORD",
          "\"type\": \"string\", \"norms\": {\"enabled\": false }, \"index\": \"not_analyzed\"");
    } else if (version >= 5) {
      return spanIndexTemplate
        .replace("TEMPLATE", version >= 6 ? "index_patterns" : "template")
        .replace("STRING", "text")
        .replace("DISABLE_ALL", "") // _all isn't supported in 6.x anyway
        .replace("KEYWORD",
          "\"type\": \"keyword\", \"norms\": false")
        .replace("\"analyzer\": \"traceId_analyzer\" }",
          "\"fielddata\": \"true\", \"analyzer\": \"traceId_analyzer\" }");
    } else {
      throw new IllegalStateException(
        "Elasticsearch 2.x, 5.x and 6.x are supported, was: " + version);
    }
  }

  private String versionSpecificDependencyLinkIndexTemplate(float version) {
    return dependencyIndexTemplate.replace("TEMPLATE",
      version >= 6 ? "index_patterns" : "template");
  }
}
