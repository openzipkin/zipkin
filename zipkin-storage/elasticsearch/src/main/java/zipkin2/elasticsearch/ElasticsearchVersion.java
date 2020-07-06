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

import com.fasterxml.jackson.core.JsonParser;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpMethod;
import java.io.IOException;
import java.util.function.Supplier;
import zipkin2.elasticsearch.internal.client.HttpCall;

import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

enum ElasticsearchVersion implements HttpCall.BodyConverter<Float> {
  INSTANCE;

  float get(HttpCall.Factory callFactory) throws IOException {
    AggregatedHttpRequest getNode = AggregatedHttpRequest.of(HttpMethod.GET, "/");
    Float version = callFactory.newCall(getNode, this, "get-node").execute();
    if (version == null) {
      throw new IllegalArgumentException("No content reading Elasticsearch version");
    }
    return version;
  }

  @Override public Float convert(JsonParser parser, Supplier<String> contentString) {
    String version = null;
    try {
      if (enterPath(parser, "version", "number") != null) version = parser.getText();
    } catch (RuntimeException | IOException possiblyParseException) {
      // EmptyCatch ignored
    }
    if (version == null) {
      throw new IllegalArgumentException(
        ".version.number not found in response: " + contentString.get());
    }
    return Float.valueOf(version.substring(0, 3));
  }
}

