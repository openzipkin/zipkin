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
