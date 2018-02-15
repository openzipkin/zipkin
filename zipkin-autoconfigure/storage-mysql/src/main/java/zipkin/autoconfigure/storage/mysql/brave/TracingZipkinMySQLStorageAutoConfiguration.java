/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin.autoconfigure.storage.mysql.brave;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListener;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.jooq.tools.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin.Endpoint;
import zipkin.autoconfigure.storage.mysql.ZipkinMySQLStorageProperties;

import static brave.Span.Kind.CLIENT;
import static zipkin.TraceKeys.SQL_QUERY;

/** Sets up the MySQL tracing in Brave as an initialization. */
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mysql")
@Configuration
public class TracingZipkinMySQLStorageAutoConfiguration extends DefaultExecuteListener {

  @Autowired
  ZipkinMySQLStorageProperties mysql;

  @Bean ExecuteListenerProvider tracingExecuteListenerProvider() {
    return new DefaultExecuteListenerProvider(this);
  }

  @Bean @ConditionalOnMissingBean(Executor.class)
  public Executor executor(CurrentTraceContext currentTraceContext) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("MySQLStorage-");
    executor.initialize();
    return currentTraceContext.executor(executor);
  }

  /** Attach the IP of the remote datasource, knowing that DNS may invalidate this */
  @Bean
  @Qualifier("mysql") Endpoint mysql() throws UnknownHostException {
    Endpoint.Builder builder = Endpoint.builder().serviceName("mysql");
    builder.parseIp(InetAddress.getByName(mysql.getHost()));
    return builder.port(mysql.getPort()).build();
  }

  @Autowired
  @Lazy // to unwind a circular dep: we are tracing the storage used by brave
  Tracing tracing;
  @Autowired
  @Qualifier("mysql")
  Endpoint mysqlEndpoint;

  @Override
  public void renderEnd(ExecuteContext ctx) {
    Tracer tracer = tracing.tracer();
    // Only join traces, don't start them. This prevents LocalCollector's thread from amplifying.
    if (tracer.currentSpan() == null) return;
    Span span = tracer.nextSpan().name(ctx.type().toString()).kind(CLIENT);
    if (!StringUtils.isBlank(ctx.sql())) span.tag(SQL_QUERY, ctx.sql());
    // Don't tag ctx.batchSQL() because it can make huge tags!
    span.remoteEndpoint(mysqlEndpoint);
    currentSpanInScope.set(tracer.withSpanInScope(span.start()));
  }

  /**
   * There's no attribute namespace shared across request and response. Hence, we need to save off
   * a reference to the span in scope, so that we can close it in the response.
   */
  final ThreadLocal<Tracer.SpanInScope> currentSpanInScope = new ThreadLocal<>();

  @Override
  public void executeEnd(ExecuteContext ctx) {
    Tracer.SpanInScope spanInScope = currentSpanInScope.get();
    if (spanInScope == null) return;
    Span span = tracing.tracer().currentSpan();
    spanInScope.close();
    span.finish();
  }
}

