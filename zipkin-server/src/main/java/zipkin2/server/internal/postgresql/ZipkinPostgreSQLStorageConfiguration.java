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
package zipkin2.server.internal.postgresql;

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
import zipkin2.storage.postgresql.v1.PostgreSQLStorage;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

@EnableConfigurationProperties(ZipkinPostgreSQLStorageProperties.class)
@ConditionalOnClass(PostgreSQLStorage.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "postgresql")
@ConditionalOnMissingBean(StorageComponent.class)
@Import(ZipkinSelfTracingPostgreSQLStorageConfiguration.class)
public class ZipkinPostgreSQLStorageConfiguration {
  @Autowired(required = false)
  ZipkinPostgreSQLStorageProperties pg;
  @Autowired(required = false) ExecuteListenerProvider pgListener;

  @Bean @ConditionalOnMissingBean
  Executor pgExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ZipkinPostgreSQLStorage-");
    executor.initialize();
    return executor;
  }

  @Bean @ConditionalOnMissingBean
  DataSource pgDataSource() {
    return pg.toDataSource();
  }

  @Bean StorageComponent storage(
    Executor pgExecutor,
    DataSource pgDataSource,
    @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
    @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
    @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys) {
    return PostgreSQLStorage.newBuilder()
      .strictTraceId(strictTraceId)
      .searchEnabled(searchEnabled)
      .autocompleteKeys(autocompleteKeys)
      .executor(pgExecutor)
      .datasource(pgDataSource)
      .listenerProvider(pgListener)
      .build();
  }
}
