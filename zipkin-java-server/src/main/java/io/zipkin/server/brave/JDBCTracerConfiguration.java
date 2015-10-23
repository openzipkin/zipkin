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

import com.github.kristofa.brave.Brave;
import com.mysql.jdbc.Driver;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.ExecuteType;
import org.jooq.impl.DefaultExecuteListener;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.jooq.tools.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Sets up the JDBC tracing in Brave as an initialization. */
@ConditionalOnClass({Driver.class})
@Configuration
public class JDBCTracerConfiguration extends DefaultExecuteListener {

  @Bean
  ExecuteListenerProvider jdbcTraceListenerProvider() {
    return new DefaultExecuteListenerProvider(this);
  }

  /** Attach the IP of the remote datasource, knowing that DNS may invalidate this */
  @Bean
  BinaryAnnotation jdbcServerAddr(@Value("${spring.datasource.url}") String jdbcUrl) throws UnknownHostException {
    URI url = URI.create(jdbcUrl.substring(5)); // strip "jdbc:"
    int ipv4 = ByteBuffer.wrap(InetAddress.getByName(url.getHost()).getAddress()).getInt();
    Endpoint endpoint = new Endpoint(ipv4, (short) url.getPort(), "zipkin-jdbc");
    BinaryAnnotation ba = new BinaryAnnotation();
    ba.setKey("sa");
    ba.setValue(new byte[]{1});
    ba.setAnnotation_type(AnnotationType.BOOL);
    ba.setHost(endpoint);
    return ba;
  }

  @Autowired
  Brave brave;
  @Autowired
  BinaryAnnotation jdbcServerAddr;

  @Override
  public void renderEnd(ExecuteContext ctx) {
    if (ctx.type() == ExecuteType.READ) { // Don't log writes (so as to not loop on collector)
      this.brave.clientTracer().startNewSpan("query");
      this.brave.clientTracer().setCurrentClientServiceName("zipkin-jdbc");

      // Temporary until https://github.com/openzipkin/brave/issues/104
      Span span = this.brave.clientSpanThreadBinder().getCurrentClientSpan();
      synchronized (span) {
        span.addToBinary_annotations(jdbcServerAddr);
      }
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
