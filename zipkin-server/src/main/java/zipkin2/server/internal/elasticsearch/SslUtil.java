/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
