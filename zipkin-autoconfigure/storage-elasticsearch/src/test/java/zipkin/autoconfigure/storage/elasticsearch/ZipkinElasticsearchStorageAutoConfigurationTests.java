/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.autoconfigure.storage.elasticsearch;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin.storage.elasticsearch.ElasticsearchStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinElasticsearchStorageAutoConfigurationTests {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  AnnotationConfigApplicationContext context;

  @After
  public void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void doesntProvidesStorageComponent_whenStorageTypeNotElasticsearch() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:cassandra");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchStorageAutoConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(ElasticsearchStorage.class);
  }

  @Test
  public void providesStorageComponent_whenStorageTypeElasticsearch() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:elasticsearch");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchStorageAutoConfiguration.class);
    context.refresh();

    assertThat(context.getBean(ElasticsearchStorage.class)).isNotNull();
  }

  @Test
  public void canOverridesProperty_hostsWithList() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
        "zipkin.storage.type:elasticsearch",
        "zipkin.storage.elasticsearch.hosts:host1:9300,host2:9300"
    );
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchStorageAutoConfiguration.class);
    context.refresh();

    assertThat(context.getBean(ZipkinElasticsearchStorageProperties.class).getHosts())
        .containsExactly("host1:9300:host2:9300");
  }
}
