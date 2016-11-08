/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.autoconfigure.storage.mysql;

import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.jooq.ExecuteListenerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin.storage.StorageComponent;
import zipkin.storage.mysql.MySQLStorage;

@Configuration
@EnableConfigurationProperties(ZipkinMySQLStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mysql")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinMySQLStorageAutoConfiguration {
  @Autowired(required = false)
  ZipkinMySQLStorageProperties mysql;

  @Autowired(required = false)
  @Qualifier("tracingExecuteListenerProvider")
  ExecuteListenerProvider listener;

  @Bean @ConditionalOnMissingBean(Executor.class) Executor executor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ZipkinMySQLStorage-");
    executor.initialize();
    return executor;
  }

  @Bean @ConditionalOnMissingBean(DataSource.class) DataSource mysqlDataSource() {
    StringBuilder url = new StringBuilder("jdbc:mysql://");
    url.append(mysql.getHost()).append(":").append(mysql.getPort());
    url.append("/").append(mysql.getDb());
    url.append("?autoReconnect=true");
    url.append("&useSSL=").append(mysql.isUseSsl());
    url.append("&useUnicode=yes&characterEncoding=UTF-8");
    HikariDataSource result = new HikariDataSource();
    result.setDriverClassName("org.mariadb.jdbc.Driver");
    result.setJdbcUrl(url.toString());
    result.setMaximumPoolSize(mysql.getMaxActive());
    result.setUsername(mysql.getUsername());
    result.setPassword(mysql.getPassword());
    return result;
  }

  @Bean StorageComponent storage(Executor executor, DataSource dataSource,
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId) {
    return MySQLStorage.builder()
        .strictTraceId(strictTraceId)
        .executor(executor)
        .datasource(dataSource)
        .listenerProvider(listener).build();
  }
}
