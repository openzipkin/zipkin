/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanState;
import com.twitter.zipkin.gen.Endpoint;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
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
import zipkin.autoconfigure.storage.mysql.ZipkinMySQLStorageProperties;

import static zipkin.TraceKeys.SQL_QUERY;

/** Sets up the MySQL tracing in Brave as an initialization. */
@ConditionalOnBean(Brave.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mysql")
@Configuration
public class TraceZipkinMySQLStorageAutoConfiguration extends DefaultExecuteListener {

  @Autowired
  ZipkinMySQLStorageProperties mysql;

  @Bean ExecuteListenerProvider tracingExecuteListenerProvider() {
    return new DefaultExecuteListenerProvider(this);
  }

  @Bean @ConditionalOnMissingBean(Executor.class)
  public Executor executor(ServerSpanState serverState) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("MySQLStorage-");
    executor.initialize();
    return command -> {
      ServerSpan currentSpan = serverState.getCurrentServerSpan();
      executor.execute(() -> {
        serverState.setCurrentServerSpan(currentSpan);
        command.run();
      });
    };
  }

  /** Attach the IP of the remote datasource, knowing that DNS may invalidate this */
  @Bean
  @Qualifier("mysql") Endpoint mysql() throws UnknownHostException {
    int ipv4 = ByteBuffer.wrap(InetAddress.getByName(mysql.getHost()).getAddress()).getInt();
    return Endpoint.builder().serviceName("mysql").ipv4(ipv4).port(mysql.getPort()).build();
  }

  @Autowired
  @Lazy // to unwind a circular dep: we are tracing the storage used by brave
  Brave brave;
  @Autowired
  @Qualifier("mysql")
  Endpoint mysqlEndpoint;

  @Override
  public void renderEnd(ExecuteContext ctx) {
    // Only join traces, don't start them. This prevents LocalCollector's thread from amplifying.
    if (brave.serverSpanThreadBinder().getCurrentServerSpan() == null ||
        brave.serverSpanThreadBinder().getCurrentServerSpan().getSpan() == null) {
      return;
    }

    brave.clientTracer().startNewSpan(ctx.type().toString().toLowerCase());
    String[] batchSQL = ctx.batchSQL();
    if (!StringUtils.isBlank(ctx.sql())) {
      brave.clientTracer().submitBinaryAnnotation(SQL_QUERY, ctx.sql());
    } else if (batchSQL.length > 0 && batchSQL[batchSQL.length - 1] != null) {
      brave.clientTracer().submitBinaryAnnotation(SQL_QUERY, StringUtils.join(batchSQL, '\n'));
    }
    brave.clientTracer()
        .setClientSent(mysqlEndpoint);
  }

  @Override
  public void executeEnd(ExecuteContext ctx) {
    if (brave.serverSpanThreadBinder().getCurrentServerSpan() == null ||
        brave.serverSpanThreadBinder().getCurrentServerSpan().getSpan() == null) {
      return;
    }
    brave.clientTracer().setClientReceived();
  }
}

