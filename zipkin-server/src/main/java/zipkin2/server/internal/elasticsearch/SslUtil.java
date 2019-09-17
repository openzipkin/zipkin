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
package zipkin2.server.internal.elasticsearch;

import java.net.URL;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.util.ResourceUtils;
import zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageProperties.Ssl;

// snippets adapted from com.linecorp.armeria.internal.spring.ArmeriaConfigurationUtil
final class SslUtil {

  static KeyManagerFactory getKeyManagerFactory(Ssl ssl) throws Exception {
    KeyStore store =
      loadKeyStore(ssl.getKeyStoreType(), ssl.getKeyStore(), ssl.getKeyStorePassword());

    KeyManagerFactory keyManagerFactory =
      KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

    String keyPassword = ssl.getKeyStorePassword();
    keyManagerFactory.init(store, keyPassword != null ? keyPassword.toCharArray() : null);
    return keyManagerFactory;
  }

  static TrustManagerFactory getTrustManagerFactory(Ssl ssl) throws Exception {
    KeyStore store =
      loadKeyStore(ssl.getTrustStoreType(), ssl.getTrustStore(), ssl.getTrustStorePassword());

    TrustManagerFactory trustManagerFactory =
      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(store);
    return trustManagerFactory;
  }

  static KeyStore loadKeyStore(String type, String resource, String password) throws Exception {
    if (resource == null) return null;
    KeyStore store = KeyStore.getInstance(type != null ? type : "JKS");
    URL url = ResourceUtils.getURL(resource);
    store.load(url.openStream(), password != null ? password.toCharArray() : null);
    return store;
  }
}
