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
package zipkin2.junit;

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
