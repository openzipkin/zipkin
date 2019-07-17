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
package zipkin2.storage.mysql.v1;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.mysql.Access;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinMySQLStorageConfigurationTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  AnnotationConfigApplicationContext context;

  @After
  public void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void doesntProvidesStorageComponent_whenStorageTypeNotMySQL() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.storage.type:cassandra").applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(MySQLStorage.class);
  }

  @Test
  public void providesStorageComponent_whenStorageTypeMySQL() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.storage.type:mysql").applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(MySQLStorage.class)).isNotNull();
  }

  @Test
  public void canOverridesProperty_username() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
        "zipkin.storage.type:mysql",
        "zipkin.storage.mysql.username:robot")
    .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(HikariDataSource.class).getUsername()).isEqualTo("robot");
  }

  @Test
  public void strictTraceId_defaultsToTrue() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.storage.type:mysql").applyTo(context);
    Access.registerMySQL(context);
    context.refresh();
    assertThat(context.getBean(MySQLStorage.class).strictTraceId).isTrue();
  }

  @Test
  public void strictTraceId_canSetToFalse() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
        "zipkin.storage.type:mysql",
        "zipkin.storage.strict-trace-id:false")
      .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(MySQLStorage.class).strictTraceId).isFalse();
  }

  @Test
  public void searchEnabled_canSetToFalse() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
      "zipkin.storage.type:mysql",
      "zipkin.storage.search-enabled:false")
      .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(MySQLStorage.class).searchEnabled).isFalse();
  }

  @Test
  public void autocompleteKeys_list() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
      "zipkin.storage.type:mysql",
      "zipkin.storage.autocomplete-keys:environment")
      .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(MySQLStorage.class).autocompleteKeys)
      .containsOnly("environment");
  }

  @Test
  public void usesJdbcUrl_whenPresent() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
        "zipkin.storage.type:mysql",
        "zipkin.storage.mysql"
      + ".jdbc-url:jdbc:mysql://host1,host2,host3/zipkin")
    .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(HikariDataSource.class).getJdbcUrl()).isEqualTo("jdbc:mysql://host1,host2,host3/zipkin");
  }

  @Test
  public void usesRegularConfig_whenBlank() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
        "zipkin.storage.type:mysql",
        "zipkin.storage.mysql.jdbc-url:",
        "zipkin.storage.mysql.host:host",
        "zipkin.storage.mysql.port:3306",
        "zipkin.storage.mysql.username:root",
        "zipkin.storage.mysql.password:secret",
        "zipkin.storage.mysql.db:zipkin")
    .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(HikariDataSource.class).getJdbcUrl()).isEqualTo("jdbc:mysql://host:3306/zipkin?autoReconnect=true&useSSL=false&useUnicode=yes&characterEncoding=UTF-8");
  }
}
