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
package zipkin2.junit;

import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.ResponseHeaders;

/**
 * Instrumentation that use {@code POST} endpoints need to survive failures. Besides simply not
 * starting the zipkin server, you can enqueue failures like this to test edge cases. For example,
 * that you log a failure when a 400 code is returned.
 */
public final class HttpFailure {

  // We use identity comparison, so catch bugs in not doing so by using a successful response.
  static final AggregatedHttpResponse DISCONNECT_DURING_REQUEST_BODY =
    AggregatedHttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);

  /** Ex a network partition occurs in the middle of the POST request */
  public static HttpFailure disconnectDuringBody() {
    return new HttpFailure(DISCONNECT_DURING_REQUEST_BODY);
  }

  /** Ex code 400 when the server cannot read the spans */
  public static HttpFailure sendErrorResponse(int code, String body) {
    return new HttpFailure(AggregatedHttpResponse.of(ResponseHeaders.of(code), HttpData.ofUtf8(body)));
  }

  /** Not exposed publicly in order to not leak armeria types. */
  final AggregatedHttpResponse response;

  HttpFailure(AggregatedHttpResponse response) {
    this.response = response;
  }
}
