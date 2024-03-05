/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.junit5;

import okhttp3.mockwebserver.MockResponse;

import static okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_REQUEST_BODY;

/**
 * Instrumentation that use {@code POST} endpoints need to survive failures. Besides simply not
 * starting the zipkin server, you can enqueue failures like this to test edge cases. For example,
 * that you log a failure when a 400 code is returned.
 */
public final class HttpFailure {

  /** Ex a network partition occurs in the middle of the POST request */
  public static HttpFailure disconnectDuringBody() {
    return new HttpFailure(new MockResponse().setSocketPolicy(DISCONNECT_DURING_REQUEST_BODY));
  }

  /** Ex code 400 when the server cannot read the spans */
  public static HttpFailure sendErrorResponse(int code, String body) {
    return new HttpFailure(new MockResponse().setResponseCode(code).setBody(body));
  }

  /** Not exposed publicly in order to not leak okhttp3 types. */
  final MockResponse response;

  HttpFailure(MockResponse response) {
    this.response = response;
  }
}
