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
package zipkin2.storage.cassandra.v1

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import zipkin2.server.internal.cassandra.Access

class ZipkinCassandraStorageConfigurationTest {
  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun doesntProvidesStorageComponent_whenStorageTypeNotCassandra() {
    TestPropertyValues.of("zipkin.storage.type:elasticsearch").applyTo(context)
    Access.registerCassandra(context)
    context.refresh()

    context.getBean(CassandraStorage::class.java)
  }

  @Test fun providesStorageComponent_whenStorageTypeCassandra() {
    TestPropertyValues.of("zipkin.storage.type:cassandra").applyTo(context)
    Access.registerCassandra(context)
    context.refresh()

    assertThat(context.getBean(CassandraStorage::class.java)).isNotNull
  }

  @Test fun canOverridesProperty_contactPoints() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra",
      "zipkin.storage.cassandra.contact-points:host1,host2" // note snake-case supported
    ).applyTo(context)
    Access.registerCassandra(context)
    context.refresh()

    assertThat(context.getBean(CassandraStorage::class.java).contactPoints).isEqualTo(
      "host1,host2")
  }

  @Test fun strictTraceId_defaultsToTrue() {
    TestPropertyValues.of("zipkin.storage.type:cassandra").applyTo(context)
    Access.registerCassandra(context)
    context.refresh()

    assertThat(context.getBean(CassandraStorage::class.java).strictTraceId).isTrue()
  }

  @Test fun strictTraceId_canSetToFalse() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra",
      "zipkin.storage.strict-trace-id:false")
      .applyTo(context)
    Access.registerCassandra(context)
    context.refresh()

    assertThat(context.getBean(CassandraStorage::class.java).strictTraceId).isFalse()
  }

  @Test fun autocompleteKeys_list() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra",
      "zipkin.storage.autocomplete-keys:environment")
      .applyTo(context)
    Access.registerCassandra(context)
    context.refresh()

    assertThat(context.getBean(CassandraStorage::class.java).autocompleteKeys)
      .containsOnly("environment")
  }

  @Test fun autocompleteTtl() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra",
      "zipkin.storage.autocomplete-ttl:60000")
      .applyTo(context)
    Access.registerCassandra(context)
    context.refresh()

    assertThat(context.getBean(CassandraStorage::class.java).autocompleteTtl)
      .isEqualTo(60000)
  }

  @Test fun autocompleteCardinality() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra",
      "zipkin.storage.autocomplete-cardinality:5000")
      .applyTo(context)
    Access.registerCassandra(context)
    context.refresh()

    assertThat(context.getBean(CassandraStorage::class.java).autocompleteCardinality)
      .isEqualTo(5000)
  }
}
