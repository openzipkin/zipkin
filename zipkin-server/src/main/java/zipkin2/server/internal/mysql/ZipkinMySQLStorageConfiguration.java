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
package zipkin2.server.internal.mysql;

import java.util.List;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.jooq.ExecuteListenerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.mysql.v1.MySQLStorage;

@Configuration
@EnableConfigurationProperties(ZipkinMySQLStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mysql")
@ConditionalOnMissingBean(StorageComponent.class)
@Import(TracingZipkinMySQLStorageConfiguration.class)
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
