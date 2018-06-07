/*
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
package zipkin.autoconfigure.storage.elasticsearch.aws;

import zipkin2.internal.Nullable;

final class AWSCredentials {
  interface Provider {
    AWSCredentials get();
  }

  final String accessKey;
  final String secretKey;
  @Nullable final String sessionToken;

  AWSCredentials(String accessKey, String secretKey, @Nullable String sessionToken) {
    if (accessKey == null) throw new NullPointerException("accessKey == null");
    if (secretKey == null) throw new NullPointerException("secretKey == null");
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.sessionToken = sessionToken;
  }
}
