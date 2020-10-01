package zipkin2.storage.postgres.v1;

import static org.assertj.core.api.Assertions.assertThat;


import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.postgres.PostgresAccess;
import zipkin2.storage.postgres.v1.PostgresStorage;

public class ZipkinPostgresStorageConfigurationTest {
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
  public void doesntProvidesStorageComponent_whenStorageTypeNotPostgres() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.storage.type:cassandra").applyTo(context);
    PostgresAccess.registerPostgres(context);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(PostgresStorage.class);
  }

  @Test
  public void providesStorageComponent_whenStorageTypePostgres() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.storage.type:postgres").applyTo(context);
    PostgresAccess.registerPostgres(context);
    context.refresh();

    assertThat(context.getBean(PostgresStorage.class)).isNotNull();
  }

  @Test
  public void canOverridesProperty_username() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
      "zipkin.storage.type:postgres",
      "zipkin.storage.postgres.username:robot")
      .applyTo(context);
    PostgresAccess.registerPostgres(context);
    context.refresh();

    assertThat(context.getBean(HikariDataSource.class).getUsername()).isEqualTo("robot");
  }

  @Test
  public void strictTraceId_defaultsToTrue() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.storage.type:postgres").applyTo(context);
    PostgresAccess.registerPostgres(context);
    context.refresh();
    assertThat(context.getBean(PostgresStorage.class).strictTraceId).isTrue();
  }

  @Test
  public void strictTraceId_canSetToFalse() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
      "zipkin.storage.type:postgres",
      "zipkin.storage.strict-trace-id:false")
      .applyTo(context);
    PostgresAccess.registerPostgres(context);
    context.refresh();

    assertThat(context.getBean(PostgresStorage.class).strictTraceId).isFalse();
  }

  @Test
  public void searchEnabled_canSetToFalse() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
      "zipkin.storage.type:postgres",
      "zipkin.storage.search-enabled:false")
      .applyTo(context);
    PostgresAccess.registerPostgres(context);
    context.refresh();

    assertThat(context.getBean(PostgresStorage.class).searchEnabled).isFalse();
  }

  @Test
  public void autocompleteKeys_list() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
      "zipkin.storage.type:postgres",
      "zipkin.storage.autocomplete-keys:environment")
      .applyTo(context);
    PostgresAccess.registerPostgres(context);
    context.refresh();

    assertThat(context.getBean(PostgresStorage.class).autocompleteKeys)
      .containsOnly("environment");
  }

  @Test
  public void usesJdbcUrl_whenPresent() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
      "zipkin.storage.type:postgres",
      "zipkin.storage.postgres"
        + ".jdbc-url:jdbc:postgresql://host1,host2,host3/zipkin")
      .applyTo(context);
    PostgresAccess.registerPostgres(context);
    context.refresh();

    assertThat(context.getBean(HikariDataSource.class).getJdbcUrl()).isEqualTo("jdbc:postgresql://host1,host2,host3/zipkin");
  }

  @Test
  public void usesRegularConfig_whenBlank() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
      "zipkin.storage.type:postgres",
      "zipkin.storage.postgres.jdbc-url:",
      "zipkin.storage.postgres.host:host",
      "zipkin.storage.postgres.port:3306",
      "zipkin.storage.postgres.username:root",
      "zipkin.storage.postgres.password:secret",
      "zipkin.storage.postgres.db:zipkin")
      .applyTo(context);
    PostgresAccess.registerPostgres(context);
    context.refresh();

    assertThat(context.getBean(HikariDataSource.class).getJdbcUrl()).isEqualTo("jdbc:postgresql://host:3306/zipkin?autoReconnect=true&useSSL=false");
  }
}
