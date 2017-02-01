/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.storage.elasticsearch;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin.autoconfigure.storage.elasticsearch.ZipkinElasticsearchStorageAutoConfiguration;
import zipkin.autoconfigure.storage.elasticsearch.ZipkinElasticsearchStorageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinElasticsearchStorageAutoConfigurationTest {

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
    es();
  }

  @Test
  public void providesStorageComponent_whenStorageTypeElasticsearch() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:elasticsearch");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchStorageAutoConfiguration.class);
    context.refresh();

    assertThat(es()).isNotNull();
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
        .containsExactly("host1:9300", "host2:9300");
  }

  @Test
  public void strictTraceId_defaultsToTrue() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:elasticsearch");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchStorageAutoConfiguration.class);
    context.refresh();
    assertThat(es().strictTraceId).isTrue();
  }

  @Test
  public void strictTraceId_canSetToFalse() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:elasticsearch");
    addEnvironment(context, "zipkin.storage.strict-trace-id:false");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchStorageAutoConfiguration.class);
    context.refresh();

    assertThat(es().strictTraceId).isFalse();
  }

  @Test
  public void dailyIndexFormat() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:elasticsearch");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchStorageAutoConfiguration.class);
    context.refresh();

    assertThat(es().indexNameFormatter.indexNameForTimestamp(0))
        .isEqualTo("zipkin-1970-01-01");
  }

  @Test
  public void dailyIndexFormat_overridingPrefix() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
        "zipkin.storage.type:elasticsearch",
        "zipkin.storage.elasticsearch.index:zipkin_prod"
    );
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchStorageAutoConfiguration.class);
    context.refresh();

    assertThat(es().indexNameFormatter.indexNameForTimestamp(0))
        .isEqualTo("zipkin_prod-1970-01-01");
  }

  @Test
  public void dailyIndexFormat_overridingDateSeparator() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
        "zipkin.storage.type:elasticsearch",
        "zipkin.storage.elasticsearch.date-separator:."
    );
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchStorageAutoConfiguration.class);
    context.refresh();

    assertThat(es().indexNameFormatter.indexNameForTimestamp(0))
        .isEqualTo("zipkin-1970.01.01");
  }

  ElasticsearchStorage es() {
    return context.getBean(ElasticsearchStorage.class);
  }
}
