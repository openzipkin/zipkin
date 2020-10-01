package zipkin2.server.internal.postgres;

import java.util.List;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.jooq.ExecuteListenerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.postgres.v1.PostgresStorage;

@EnableConfigurationProperties(ZipkinPostgresStorageProperties.class)
@ConditionalOnClass(PostgresStorage.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "postgres")
@ConditionalOnMissingBean(StorageComponent.class)
@Import(ZipkinSelfTracingPostgresStorageConfiguration.class)
public class ZipkinPostgresStorageConfiguration {
  @Autowired(required = false) ZipkinPostgresStorageProperties postgres;
  @Autowired(required = false) ExecuteListenerProvider postgresListener;

  @Bean
  @ConditionalOnMissingBean
  Executor postgresExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ZipkinPostgresStorage-");
    executor.initialize();
    return executor;
  }

  @Bean @ConditionalOnMissingBean
  DataSource postgresDataSource() {
    return postgres.toDataSource();
  }

  @Bean StorageComponent storage(
    Executor postgresExecutor,
    DataSource postgresDataSource,
    @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
    @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
    @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys) {
    return PostgresStorage.newBuilder()
      .strictTraceId(strictTraceId)
      .searchEnabled(searchEnabled)
      .autocompleteKeys(autocompleteKeys)
      .executor(postgresExecutor)
      .datasource(postgresDataSource)
      .listenerProvider(postgresListener)
      .build();
  }
}
