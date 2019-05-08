/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.elasticsearch

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import zipkin2.server.internal.elasticsearch.Access
import java.util.concurrent.TimeUnit

class ZipkinElasticsearchStorageAutoConfigurationTest {
  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()
  internal fun es() = context.getBean(ElasticsearchStorage::class.java)

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun doesntProvideStorageComponent_whenStorageTypeNotElasticsearch() {
    TestPropertyValues.of("zipkin.storage.type:cassandra").applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    es()
  }

  @Test fun providesStorageComponent_whenStorageTypeElasticsearchAndHostsAreUrls() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es()).isNotNull
  }

  @Test fun canOverridesProperty_hostsWithList() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200,http://host2:9200")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().hostsSupplier().get())
      .containsExactly("http://host1:9200", "http://host2:9200")
  }

  @Test fun configuresPipeline() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.pipeline:zipkin")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().pipeline()).isEqualTo("zipkin")
  }

  @Test fun configuresMaxRequests() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.max-requests:200")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().maxRequests()).isEqualTo(200)
  }

  /** This helps ensure old setups don't break (provided they have http port 9200 open)  */
  @Test fun coersesPort9300To9200() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:host1:9300")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().hostsSupplier().get()).containsExactly("http://host1:9200")
  }

  @Test fun httpPrefixOptional() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:host1:9200")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().hostsSupplier().get()).containsExactly("http://host1:9200")
  }

  @Test fun defaultsToPort9200() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:host1")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().hostsSupplier().get()).containsExactly("http://host1:9200")
  }

  @Configuration
  open class InterceptorConfiguration {

    @Bean @Qualifier("zipkinElasticsearchHttp") open fun one(): Interceptor {
      return one
    }

    @Bean @Qualifier("zipkinElasticsearchHttp") open fun two(): Interceptor {
      return two
    }

    companion object {
      val one: Interceptor = Interceptor { chain -> chain.proceed(chain.request()) }
      val two: Interceptor = Interceptor { chain -> chain.proceed(chain.request()) }
    }
  }

  /** Ensures we can wire up network interceptors, such as for logging or authentication  */
  @Test fun usesInterceptorsQualifiedWith_zipkinElasticsearchHttp() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:host1:9200")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.register(InterceptorConfiguration::class.java)
    context.refresh()

    assertThat(context.getBean(OkHttpClient::class.java).networkInterceptors())
      .containsOnlyOnce(InterceptorConfiguration.one, InterceptorConfiguration.two)
  }

  @Test fun timeout_defaultsTo10Seconds() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:host1:9200")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    val client = context.getBean(OkHttpClient::class.java)
    assertThat(client.connectTimeoutMillis()).isEqualTo(10000)
    assertThat(client.readTimeoutMillis()).isEqualTo(10000)
    assertThat(client.writeTimeoutMillis()).isEqualTo(10000)
  }

  @Test fun timeout_override() {
    val timeout = 30000
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.timeout:$timeout")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    val client = context.getBean(OkHttpClient::class.java)
    assertThat(client.connectTimeoutMillis()).isEqualTo(timeout)
    assertThat(client.readTimeoutMillis()).isEqualTo(timeout)
    assertThat(client.writeTimeoutMillis()).isEqualTo(timeout)
  }

  @Test fun strictTraceId_defaultsToTrue() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()
    assertThat(es().strictTraceId()).isTrue()
  }

  @Test fun strictTraceId_canSetToFalse() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.strict-trace-id:false")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().strictTraceId()).isFalse()
  }

  @Test fun dailyIndexFormat() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().indexNameFormatter().formatTypeAndTimestamp("span", 0))
      .isEqualTo("zipkin*span-1970-01-01")
  }

  @Test fun dailyIndexFormat_overridingPrefix() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.index:zipkin_prod")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().indexNameFormatter().formatTypeAndTimestamp("span", 0))
      .isEqualTo("zipkin_prod*span-1970-01-01")
  }

  @Test fun dailyIndexFormat_overridingDateSeparator() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.date-separator:.")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().indexNameFormatter().formatTypeAndTimestamp("span", 0))
      .isEqualTo("zipkin*span-1970.01.01")
  }

  @Test fun dailyIndexFormat_overridingDateSeparator_empty() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.date-separator:")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().indexNameFormatter().formatTypeAndTimestamp("span", 0))
      .isEqualTo("zipkin*span-19700101")
  }

  @Test(expected = BeanCreationException::class)
  fun dailyIndexFormat_overridingDateSeparator_invalidToBeMultiChar() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.date-separator:blagho")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)

    context.refresh()
  }

  @Test fun namesLookbackAssignedFromQueryLookback() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.query.lookback:" + TimeUnit.DAYS.toMillis(2))
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().namesLookback()).isEqualTo(TimeUnit.DAYS.toMillis(2).toInt())
  }

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun doesntProvideBasicAuthInterceptor_whenBasicAuthUserNameandPasswordNotConfigured() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    context.getBean(Interceptor::class.java)
  }

  @Test fun providesBasicAuthInterceptor_whenBasicAuthUserNameAndPasswordConfigured() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:http://host1:9200",
      "zipkin.storage.elasticsearch.username:somename",
      "zipkin.storage.elasticsearch.password:pass")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(context.getBean(OkHttpClient::class.java).networkInterceptors())
      .extracting<String, RuntimeException> { i -> i.javaClass.name }
      .contains("zipkin2.server.internal.elasticsearch.BasicAuthInterceptor")
  }

  @Test fun searchEnabled_false() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.search-enabled:false")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().searchEnabled()).isFalse()
  }

  @Test fun autocompleteKeys_list() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.autocomplete-keys:environment")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().autocompleteKeys())
      .containsOnly("environment")
  }

  @Test fun autocompleteTtl() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.autocomplete-ttl:60000")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().autocompleteTtl())
      .isEqualTo(60000)
  }

  @Test fun autocompleteCardinality() {
    TestPropertyValues.of(
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.autocomplete-cardinality:5000")
      .applyTo(context)
    Access.registerElasticsearchHttp(context)
    context.refresh()

    assertThat(es().autocompleteCardinality())
      .isEqualTo(5000)
  }
}
