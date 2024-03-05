/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
import zipkin2.elasticsearch.internal.client.HttpCall;

/** Ensures the index template exists and saves off the version */
final class EnsureIndexTemplate {

  /**
   * This is a blocking call, used inside a lazy. That's because no writes should occur until the
   * template is available.
   */
  static void ensureIndexTemplate(HttpCall.Factory callFactory, String templateUrl,
      String indexTemplate) throws IOException {
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
