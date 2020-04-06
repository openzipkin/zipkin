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
package zipkin2.server.internal.elasticsearch;

import java.util.Base64;
import java.util.Optional;
import zipkin2.internal.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Generate Elasticsearch basic user credentials.
 *
 * <p>Ref: <a href="https://www.elastic.co/guide/en/x-pack/current/how-security-works.html"> How
 * Elasticsearch security works</a></p>
 */
final class BasicCredentials {

  private volatile String basicCredentials;

  BasicCredentials() {

  }

  BasicCredentials(String username, String password) {
    updateCredentials(username, password);
  }

  void updateCredentials(String username, String password) {
    String token = username + ':' + password;
    basicCredentials = "Basic " + Base64.getEncoder().encodeToString(token.getBytes(UTF_8));
  }

  @Nullable
  String getCredentials() {
    return basicCredentials;
  }
}
