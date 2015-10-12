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

package io.zipkin.server.brave;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.ExecuteType;
import org.jooq.impl.DefaultExecuteListener;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.jooq.tools.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.kristofa.brave.Brave;
import com.mysql.jdbc.Driver;

/** Sets up the JDBC tracing in Brave as an initialization. */
@ConditionalOnClass({Driver.class})
@Configuration
public class JDBCTracerConfiguration extends DefaultExecuteListener {

  @Bean
  ExecuteListenerProvider jdbcTraceListenerProvider() {
    return new DefaultExecuteListenerProvider(this);
  }

  @Autowired
  Brave brave;

  @Override
  public void renderEnd(ExecuteContext ctx) {
    if (ctx.type() == ExecuteType.READ) { // Don't log writes (so as to not loop on collector)
      this.brave.clientTracer().startNewSpan("query");
      this.brave.clientTracer().setCurrentClientServiceName("zipkin-jdbc");

      String[] batchSQL = ctx.batchSQL();
      if (!StringUtils.isBlank(ctx.sql())) {
        this.brave.clientTracer().submitBinaryAnnotation("jdbc.query", ctx.sql());
      } else if (batchSQL.length > 0 && batchSQL[batchSQL.length - 1] != null) {
        this.brave.clientTracer().submitBinaryAnnotation("jdbc.query", StringUtils.join(batchSQL, '\n'));
      }
      this.brave.clientTracer().setClientSent();
    }
  }

  @Override
  public void executeEnd(ExecuteContext ctx) {
    if (ctx.type() == ExecuteType.READ) { // Don't log writes (so as to not loop on collector)
      this.brave.clientTracer().setClientReceived();
    }
  }
}
