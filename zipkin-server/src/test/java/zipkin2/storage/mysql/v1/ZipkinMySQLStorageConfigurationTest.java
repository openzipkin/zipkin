/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.mysql.Access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ZipkinMySQLStorageConfigurationTest {

  AnnotationConfigApplicationContext context;

  @AfterEach void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test void doesntProvidesStorageComponent_whenStorageTypeNotMySQL() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context = new AnnotationConfigApplicationContext();
      TestPropertyValues.of("zipkin.storage.type:cassandra").applyTo(context);
      Access.registerMySQL(context);
      context.refresh();
      context.getBean(MySQLStorage.class);
    });
  }

  @Test void providesStorageComponent_whenStorageTypeMySQL() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.storage.type:mysql").applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(MySQLStorage.class)).isNotNull();
  }

  @Test void canOverridesProperty_username() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
        "zipkin.storage.type:mysql",
        "zipkin.storage.mysql.username:robot")
    .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(HikariDataSource.class).getUsername()).isEqualTo("robot");
  }

  @Test void strictTraceId_defaultsToTrue() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.storage.type:mysql").applyTo(context);
    Access.registerMySQL(context);
    context.refresh();
    assertThat(context.getBean(MySQLStorage.class).strictTraceId).isTrue();
  }

  @Test void strictTraceId_canSetToFalse() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
        "zipkin.storage.type:mysql",
        "zipkin.storage.strict-trace-id:false")
      .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(MySQLStorage.class).strictTraceId).isFalse();
  }

  @Test void searchEnabled_canSetToFalse() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
      "zipkin.storage.type:mysql",
      "zipkin.storage.search-enabled:false")
      .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(MySQLStorage.class).searchEnabled).isFalse();
  }

  @Test void autocompleteKeys_list() {
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

  @Test void usesJdbcUrl_whenPresent() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
        "zipkin.storage.type:mysql",
        """
      zipkin.storage.mysql\
      .jdbc-url:jdbc:mariadb://host1,host2,host3/zipkin\
      """)
    .applyTo(context);
    Access.registerMySQL(context);
    context.refresh();

    assertThat(context.getBean(HikariDataSource.class).getJdbcUrl())
      .isEqualTo("jdbc:mariadb://host1,host2,host3/zipkin");
  }

  @Test void usesRegularConfig_whenBlank() {
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

    assertThat(context.getBean(HikariDataSource.class).getJdbcUrl())
      .isEqualTo("jdbc:mariadb://host:3306/zipkin?autoReconnect=true&useSSL=false&useUnicode=yes&characterEncoding=UTF-8");
  }
}
