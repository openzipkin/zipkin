/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import java.util.Base64;
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
