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
package zipkin2.storage.mysql.v1

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import zipkin2.server.internal.mysql.Access

class ZipkinMySQLStorageConfigurationTest {
  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun doesntProvidesStorageComponent_whenStorageTypeNotMySQL() {
    TestPropertyValues.of("zipkin.storage.type:cassandra").applyTo(context)
    Access.registerMySQL(context)
    context.refresh()

    context.getBean(MySQLStorage::class.java)
  }

  @Test fun providesStorageComponent_whenStorageTypeMySQL() {
    TestPropertyValues.of("zipkin.storage.type:mysql").applyTo(context)
    Access.registerMySQL(context)
    context.refresh()

    assertThat(context.getBean(MySQLStorage::class.java)).isNotNull
  }

  @Test fun canOverridesProperty_username() {
    TestPropertyValues.of(
      "zipkin.storage.type:mysql",
      "zipkin.storage.mysql.username:robot")
      .applyTo(context)
    Access.registerMySQL(context)
    context.refresh()

    assertThat(context.getBean(HikariDataSource::class.java).username).isEqualTo("robot")
  }

  @Test fun strictTraceId_defaultsToTrue() {
    TestPropertyValues.of("zipkin.storage.type:mysql").applyTo(context)
    Access.registerMySQL(context)
    context.refresh()

    assertThat(context.getBean(MySQLStorage::class.java).strictTraceId).isTrue()
  }

  @Test fun strictTraceId_canSetToFalse() {
    TestPropertyValues.of(
      "zipkin.storage.type:mysql",
      "zipkin.storage.strict-trace-id:false")
      .applyTo(context)
    Access.registerMySQL(context)
    context.refresh()

    assertThat(context.getBean(MySQLStorage::class.java).strictTraceId).isFalse()
  }

  @Test fun searchEnabled_canSetToFalse() {
    TestPropertyValues.of(
      "zipkin.storage.type:mysql",
      "zipkin.storage.search-enabled:false")
      .applyTo(context)
    Access.registerMySQL(context)
    context.refresh()

    assertThat(context.getBean(MySQLStorage::class.java).searchEnabled).isFalse()
  }

  @Test fun autocompleteKeys_list() {
    TestPropertyValues.of(
      "zipkin.storage.type:mysql",
      "zipkin.storage.autocomplete-keys:environment")
      .applyTo(context)
    Access.registerMySQL(context)
    context.refresh()

    assertThat(context.getBean(MySQLStorage::class.java).autocompleteKeys)
      .containsOnly("environment")
  }

  @Test fun usesJdbcUrl_whenPresent() {
    TestPropertyValues.of(
      "zipkin.storage.type:mysql",
      "zipkin.storage.mysql" + ".jdbc-url:jdbc:mysql://host1,host2,host3/zipkin")
      .applyTo(context)
    Access.registerMySQL(context)
    context.refresh()

    assertThat(context.getBean(HikariDataSource::class.java).jdbcUrl).isEqualTo(
      "jdbc:mysql://host1,host2,host3/zipkin")
  }

  @Test fun usesRegularConfig_whenBlank() {
    TestPropertyValues.of(
      "zipkin.storage.type:mysql",
      "zipkin.storage.mysql.jdbc-url:",
      "zipkin.storage.mysql.host:host",
      "zipkin.storage.mysql.port:3306",
      "zipkin.storage.mysql.username:root",
      "zipkin.storage.mysql.password:secret",
      "zipkin.storage.mysql.db:zipkin")
      .applyTo(context)
    Access.registerMySQL(context)
    context.refresh()

    assertThat(context.getBean(HikariDataSource::class.java).jdbcUrl).isEqualTo(
      "jdbc:mysql://host:3306/zipkin?autoReconnect=true&useSSL=false&useUnicode=yes&characterEncoding=UTF-8")
  }
}
