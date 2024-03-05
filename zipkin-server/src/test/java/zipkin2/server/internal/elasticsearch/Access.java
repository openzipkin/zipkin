/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.spring.Ssl;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.NoOpMeterRegistryConfiguration;

/** opens package access for testing */
public final class Access {

  public static void registerElasticsearch(AnnotationConfigApplicationContext context) {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      NoOpMeterRegistryConfiguration.class,
      ZipkinElasticsearchStorageConfiguration.class);
  }

  public static ClientFactoryBuilder configureSsl(ClientFactoryBuilder builder, Ssl ssl) {
    ZipkinElasticsearchStorageProperties.Ssl eSsl = new ZipkinElasticsearchStorageProperties.Ssl();
    eSsl.setKeyStore(ssl.getKeyStore());
    eSsl.setKeyStorePassword(ssl.getKeyStorePassword());
    eSsl.setKeyStoreType(ssl.getKeyStoreType());
    eSsl.setTrustStore(ssl.getTrustStore());
    eSsl.setTrustStorePassword(ssl.getTrustStorePassword());
    eSsl.setTrustStoreType(ssl.getTrustStoreType());
    try {
      return ZipkinElasticsearchStorageConfiguration.configureSsl(builder, eSsl);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
