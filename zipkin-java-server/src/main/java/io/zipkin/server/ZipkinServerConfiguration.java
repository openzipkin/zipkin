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

import static java.util.Collections.emptyList;

import javax.sql.DataSource;

import org.jooq.conf.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.service.ThriftServer;
import com.facebook.swift.service.ThriftServerConfig;
import com.facebook.swift.service.ThriftServiceProcessor;

import io.zipkin.SpanStore;
import io.zipkin.jdbc.JDBCSpanStore;
import io.zipkin.scribe.Scribe;
import io.zipkin.scribe.ScribeSpanConsumer;
import io.zipkin.server.ZipkinServerProperties.Store.Type;

@Configuration
@EnableConfigurationProperties(ZipkinServerProperties.class)
public class ZipkinServerConfiguration {

  @Autowired
  ZipkinServerProperties server;

  @Autowired(required=false)
  DataSource datasource;

  @Bean
  SpanStore provideSpanStore(DataSource datasource) {
    if (datasource !=null && this.server.getStore().getType()==Type.jdbc) {
      return new JDBCSpanStore(datasource, new Settings());
    } else {
      return new InMemorySpanStore();
    }
  }

  @Bean
  Scribe scribeConsumer(SpanStore spanStore) {
    return new ScribeSpanConsumer(spanStore::accept);
  }

  @Bean
  ThriftServer scribeServer(Scribe scribe) {
    ThriftServiceProcessor processor = new ThriftServiceProcessor(new ThriftCodecManager(), emptyList(), scribe);
    return new ThriftServer(processor, new ThriftServerConfig()
        .setBindAddress("localhost")
        .setPort(this.server.getCollector().getPort()));
  }
}

@ConfigurationProperties("zipkin")
class ZipkinServerProperties {
  private Collector collector = new Collector();
  private Store store = new Store();
  public Collector getCollector() {
    return this.collector;
  }
  public Store getStore() {
    return this.store;
  }
  static class Store {
    enum Type {
      jdbc, inMemory;
    }
    private Type type = Type.inMemory;
    public Type getType() {
      return this.type;
    }
    public void setType(Type type) {
      this.type = type;
    }
  }
  static class Collector {
    private int port = 9410;

    public int getPort() {
      return this.port;
    }

    public void setPort(int port) {
      this.port = port;
    }
  }
}
