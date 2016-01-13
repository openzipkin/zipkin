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
package io.zipkin.server;

import com.github.kristofa.brave.Brave;
import io.zipkin.Codec;
import io.zipkin.Sampler;
import io.zipkin.SpanStore;
import io.zipkin.jdbc.JDBCSpanStore;
import io.zipkin.server.ZipkinServerProperties.Store.Type;
import io.zipkin.server.brave.TraceWritesSpanStore;
import javax.sql.DataSource;
import org.jooq.ExecuteListenerProvider;
import org.jooq.conf.Settings;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

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
  @ConditionalOnMissingBean(Codec.Factory.class)
  Codec.Factory codecFactory() {
    return Codec.FACTORY;
  }

  @Bean
  @ConditionalOnMissingBean(Sampler.class) Sampler traceIdSampler(@Value("${zipkin.collector.sample-rate:1.0}") float rate) {
    return Sampler.create(rate);
  }

  @Bean
  SpanStore spanStore() {
    SpanStore result;
    if (datasource != null && server.getStore().getType() == Type.mysql) {
      result = new JDBCSpanStore(datasource, new Settings().withRenderSchema(false), listener);
    } else {
      result = new InMemorySpanStore();
    }
    return result;
  }

  @Configuration
  @ConditionalOnClass(Brave.class)
  protected static class BraveSpanStoreEnhancer implements BeanPostProcessor {

    @Autowired(required = false)
    Brave brave;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
        throws BeansException {
      return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
        throws BeansException {
      if (bean instanceof SpanStore && brave != null) {
        return new TraceWritesSpanStore(brave, (SpanStore) bean);
      }
      return bean;
    }

  }

}
