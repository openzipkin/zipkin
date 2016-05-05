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
package zipkin.server.brave;

import com.github.kristofa.brave.Brave;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.ExecuteType;
import org.jooq.impl.DefaultExecuteListener;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.jooq.tools.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import zipkin.Endpoint;
import zipkin.server.ConditionalOnSelfTracing;
import zipkin.server.ZipkinMySQLProperties;

/** Sets up the JDBC tracing in Brave as an initialization. */
@EnableConfigurationProperties(ZipkinMySQLProperties.class)
@ConditionalOnSelfTracing(storageType = "mysql")
@Configuration
public class JDBCTracerConfiguration extends DefaultExecuteListener {

  @Autowired
  ZipkinMySQLProperties mysql;

  @Bean
  ExecuteListenerProvider tracingExecuteListenerProvider() {
    return new DefaultExecuteListenerProvider(this);
  }

  /** Attach the IP of the remote datasource, knowing that DNS may invalidate this */
  @Bean
  @Qualifier("jdbc")
  Endpoint jdbc() throws UnknownHostException {
    int ipv4 = ByteBuffer.wrap(InetAddress.getByName(mysql.getHost()).getAddress()).getInt();
    return Endpoint.create("mysql", ipv4, mysql.getPort());
  }

  @Autowired
  @Lazy // to unwind a circular dep: we are tracing the storage used by brave
  Brave brave;
  @Autowired
  @Qualifier("jdbc")
  Endpoint jdbcEndpoint;

  @Override
  public void renderEnd(ExecuteContext ctx) {
    if (ctx.type() == ExecuteType.READ) { // Don't log writes (so as to not loop on collector)
      brave.clientTracer().startNewSpan("query");
      String[] batchSQL = ctx.batchSQL();
      if (!StringUtils.isBlank(ctx.sql())) {
        brave.clientTracer().submitBinaryAnnotation("jdbc.query", ctx.sql());
      } else if (batchSQL.length > 0 && batchSQL[batchSQL.length - 1] != null) {
        brave.clientTracer().submitBinaryAnnotation("jdbc.query", StringUtils.join(batchSQL, '\n'));
      }
      brave.clientTracer().setClientSent(jdbcEndpoint.ipv4, jdbcEndpoint.port, jdbcEndpoint.serviceName);
    }
  }

  @Override
  public void executeEnd(ExecuteContext ctx) {
    if (ctx.type() == ExecuteType.READ) { // Don't log writes (so as to not loop on collector)
      brave.clientTracer().setClientReceived();
    }
  }
}
