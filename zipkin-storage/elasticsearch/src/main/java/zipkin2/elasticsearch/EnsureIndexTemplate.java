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

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import java.io.FileNotFoundException;
import java.io.IOException;
import zipkin2.elasticsearch.internal.client.HttpCall.Factory;

/** Ensures the index template exists and saves off the version */
final class EnsureIndexTemplate {

  /**
   * This is a blocking call, used inside a lazy. That's because no writes should occur until the
   * template is available.
   */
  static void ensureIndexTemplate(Factory callFactory, String templateUrl, String indexTemplate)
    throws IOException {
    AggregatedHttpRequest getTemplate = AggregatedHttpRequest.of(HttpMethod.GET, templateUrl);
    try {
      callFactory.newCall(getTemplate, BodyConverters.NULL, "get-template").execute();
    } catch (FileNotFoundException e) { // TODO: handle 404 slightly more nicely
      AggregatedHttpRequest updateTemplate = AggregatedHttpRequest.of(
        RequestHeaders.of(
          HttpMethod.PUT, templateUrl, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8),
        HttpData.ofUtf8(indexTemplate));
      callFactory.newCall(updateTemplate, BodyConverters.NULL, "update-template").execute();
    }
  }
}
