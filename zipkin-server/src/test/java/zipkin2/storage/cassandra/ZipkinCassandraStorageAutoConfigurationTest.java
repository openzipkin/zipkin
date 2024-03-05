/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.cassandra3.Access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipkinCassandraStorageAutoConfigurationTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesntProvidesStorageComponent_whenStorageTypeNotCassandra() {
    TestPropertyValues.of("zipkin.storage.type:elasticsearch").applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThatThrownBy(() -> context.getBean(CassandraStorage.class))
      .isInstanceOf(NoSuchBeanDefinitionException.class);
  }

  @Test void providesStorageComponent_whenStorageTypeCassandra() {
    TestPropertyValues.of("zipkin.storage.type:cassandra3").applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class)).isNotNull();
  }

  @Test void canOverridesProperty_contactPoints() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra3",
      "zipkin.storage.cassandra3.contact-points:host1,host2" // note snake-case supported
    ).applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class).contactPoints).isEqualTo("host1,host2");
  }

  @Test void strictTraceId_defaultsToTrue() {
    TestPropertyValues.of("zipkin.storage.type:cassandra3").applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class).strictTraceId).isTrue();
  }

  @Test void strictTraceId_canSetToFalse() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra3",
      "zipkin.storage.strict-trace-id:false")
      .applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class).strictTraceId).isFalse();
  }

  @Test void searchEnabled_canSetToFalse() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra3",
      "zipkin.storage.search-enabled:false")
      .applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class).searchEnabled).isFalse();
  }

  @Test void autocompleteKeys_list() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra3",
      "zipkin.storage.autocomplete-keys:environment")
      .applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class).autocompleteKeys)
      .containsOnly("environment");
  }

  @Test void autocompleteTtl() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra3",
      "zipkin.storage.autocomplete-ttl:60000")
      .applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class).autocompleteTtl)
      .isEqualTo(60000);
  }

  @Test void autocompleteCardinality() {
    TestPropertyValues.of(
      "zipkin.storage.type:cassandra3",
      "zipkin.storage.autocomplete-cardinality:5000")
      .applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class).autocompleteCardinality)
      .isEqualTo(5000);
  }

  @Test void useSsl() {
    TestPropertyValues.of(
        "zipkin.storage.type:cassandra3",
        "zipkin.storage.cassandra3.use-ssl:true")
      .applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class).useSsl).isTrue();
    assertThat(context.getBean(CassandraStorage.class).sslHostnameValidation).isTrue();
  }

  @Test void sslHostnameValidation_canSetToFalse() {
    TestPropertyValues.of(
        "zipkin.storage.type:cassandra3",
        "zipkin.storage.cassandra3.use-ssl:true",
        "zipkin.storage.cassandra3.ssl-hostname-validation:false"
      )
      .applyTo(context);
    Access.registerCassandra3(context);
    context.refresh();

    assertThat(context.getBean(CassandraStorage.class).useSsl).isTrue();
    assertThat(context.getBean(CassandraStorage.class).sslHostnameValidation).isFalse();
  }
}
