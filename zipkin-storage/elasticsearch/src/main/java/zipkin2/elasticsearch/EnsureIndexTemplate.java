/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import zipkin2.elasticsearch.internal.client.RequestBuilder;

import java.io.IOException;

/** Ensures the index template exists and saves off the version */
final class EnsureIndexTemplate {

  /**
   * This is a blocking call, used inside a lazy. That's because no writes should occur until the
   * template is available.
   */
  static void apply(RestClient client, String name, String indexTemplate)
      throws IOException {
    Request getTemplate = RequestBuilder.get("_template", name).tag("get-template").build();
    try {
      client.performRequest(getTemplate);
    } catch (ResponseException e) { // TODO: handle 404 slightly more nicely
      Request updateTemplate =
          RequestBuilder.put("_template", name)
            .tag("update-template")
            .jsonEntity(indexTemplate)
            .build();
      client.performRequest(updateTemplate);
    }
  }
}
