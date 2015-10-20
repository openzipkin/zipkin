/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.server;

import javax.sql.DataSource;

import org.jooq.ExecuteListenerProvider;
import org.jooq.conf.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import io.zipkin.SpanStore;
import io.zipkin.jdbc.JDBCSpanStore;
import io.zipkin.server.ZipkinServerProperties.Store.Type;

@Configuration
@EnableConfigurationProperties(ZipkinServerProperties.class)
@EnableAsync(proxyTargetClass=true)
public class ZipkinServerConfiguration {

  @Autowired
  ZipkinServerProperties server;

  @Autowired(required = false)
  DataSource datasource;

  @Autowired(required = false)
  @Qualifier("jdbcTraceListenerProvider")
  ExecuteListenerProvider listener;

  @Bean
  SpanStore spanStore() {
    if (this.datasource != null && this.server.getStore().getType() == Type.mysql) {
      return new JDBCSpanStore(this.datasource, new Settings().withRenderSchema(false), this.listener);
    } else {
      return new InMemorySpanStore();
    }
  }
}
