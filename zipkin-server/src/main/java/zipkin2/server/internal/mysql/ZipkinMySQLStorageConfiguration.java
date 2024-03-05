/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.mysql;

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
import zipkin2.storage.mysql.v1.MySQLStorage;

@EnableConfigurationProperties(ZipkinMySQLStorageProperties.class)
@ConditionalOnClass(MySQLStorage.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mysql")
@ConditionalOnMissingBean(StorageComponent.class)
@Import(ZipkinSelfTracingMySQLStorageConfiguration.class)
public class ZipkinMySQLStorageConfiguration {
  @Autowired(required = false) ZipkinMySQLStorageProperties mysql;
  @Autowired(required = false) ExecuteListenerProvider mysqlListener;

  @Bean @ConditionalOnMissingBean
  Executor mysqlExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ZipkinMySQLStorage-");
    executor.initialize();
    return executor;
  }

  @Bean @ConditionalOnMissingBean
  DataSource mysqlDataSource() {
    return mysql.toDataSource();
  }

  @Bean StorageComponent storage(
    Executor mysqlExecutor,
    DataSource mysqlDataSource,
    @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
    @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
    @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys) {
    return MySQLStorage.newBuilder()
      .strictTraceId(strictTraceId)
      .searchEnabled(searchEnabled)
      .autocompleteKeys(autocompleteKeys)
      .executor(mysqlExecutor)
      .datasource(mysqlDataSource)
      .listenerProvider(mysqlListener)
      .build();
  }
}
