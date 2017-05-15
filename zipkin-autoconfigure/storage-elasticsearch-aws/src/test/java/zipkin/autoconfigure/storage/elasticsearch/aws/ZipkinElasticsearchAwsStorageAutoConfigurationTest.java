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
package zipkin.autoconfigure.storage.elasticsearch.aws;

import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin.autoconfigure.storage.elasticsearch.http.ZipkinElasticsearchOkHttpAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinElasticsearchAwsStorageAutoConfigurationTest {

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
  public void doesntProvideAWSSignatureVersion4_whenStorageTypeNotElasticsearch() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:cassandra");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchAwsStorageAutoConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(AWSSignatureVersion4.class);
  }

  @Test
  public void providesAWSSignatureVersion4_whenStorageTypeElasticsearchAndHostsAreAwsUrls() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
        "zipkin.storage.type:elasticsearch",
        "zipkin.storage.elasticsearch.hosts:https://search-domain-xyzzy.us-west-2.es.amazonaws.com"
    );
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchOkHttpAutoConfiguration.class,
        ZipkinElasticsearchAwsStorageAutoConfiguration.class);
    context.refresh();

    assertThat(context.getBean(OkHttpClient.class).networkInterceptors())
        .extracting(i -> i.getClass())
        .contains((Class) AWSSignatureVersion4.class);
  }

  @Test
  public void providesAWSSignatureVersion4_whenStorageTypeElasticsearchAndDomain() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
        "zipkin.storage.type:elasticsearch",
        "zipkin.storage.elasticsearch.aws.domain:foobar",
        "zipkin.storage.elasticsearch.aws.region:us-west-2"
    );
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchOkHttpAutoConfiguration.class,
        ZipkinElasticsearchAwsStorageAutoConfiguration.class);
    context.refresh();

    assertThat(context.getBean(OkHttpClient.class).networkInterceptors())
        .extracting(i -> i.getClass())
        .contains((Class) AWSSignatureVersion4.class);
  }

  @Test
  public void doesntProvidesAWSSignatureVersion4_whenStorageTypeElasticsearchAndHostsNotUrls() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.storage.type:elasticsearch");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchAwsStorageAutoConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(AWSSignatureVersion4.class);
  }

  @Test
  public void doesntProvideAWSSignatureVersion4_whenStorageTypeElasticsearchAndHostsNotAwsUrls() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
        "zipkin.storage.type:elasticsearch",
        "zipkin.storage.elasticsearch.hosts:https://localhost:9200"
    );
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinElasticsearchAwsStorageAutoConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(AWSSignatureVersion4.class);
  }
}
