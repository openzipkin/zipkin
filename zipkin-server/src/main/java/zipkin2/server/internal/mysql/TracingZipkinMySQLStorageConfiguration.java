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

import brave.Span;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalSpan;
import com.linecorp.armeria.common.RequestContext;
import java.util.concurrent.Executor;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListener;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.server.internal.ConditionalOnSelfTracing;

/** Sets up the MySQL tracing in Brave as an initialization. */
@ConditionalOnSelfTracing
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mysql")
@Configuration
class TracingZipkinMySQLStorageConfiguration extends DefaultExecuteListener {

  @Autowired ZipkinMySQLStorageProperties mysql;
  @Autowired CurrentTraceContext currentTraceContext;
  @Autowired ThreadLocalSpan threadLocalSpan;

  @Bean ExecuteListenerProvider mysqlListener() {
    return new DefaultExecuteListenerProvider(this);
  }

  @Bean Executor mysqlExecutor() {
    return makeContextAware(
      new ZipkinMySQLStorageConfiguration().mysqlExecutor(),
      currentTraceContext
    );
  }

  /**
   * Decorates the input such that the {@link RequestContext#current() current request context} and
   * the and the {@link CurrentTraceContext#get() current trace context} at assembly time is made
   * current when task is executed.
   */
  static Executor makeContextAware(Executor delegate, CurrentTraceContext currentTraceContext) {
    class TracingCurrentRequestContextExecutor implements Executor {
      @Override public void execute(Runnable task) {
        delegate.execute(RequestContext.current().makeContextAware(currentTraceContext.wrap(task)));
      }
    }
    return new TracingCurrentRequestContextExecutor();
  }

  @Override public void renderEnd(ExecuteContext ctx) {
    // don't start new traces (to prevent amplifying writes to local storage)
    if (currentTraceContext.get() == null) return;

    // Gets the next span (and places it in scope) so code between here and postProcess can read it
    Span span = threadLocalSpan.next();
    if (span == null || span.isNoop()) return;

    String sql = ctx.sql();
    int spaceIndex = sql.indexOf(' '); // Allow span names of single-word statements like COMMIT
    span.kind(Span.Kind.CLIENT).name(spaceIndex == -1 ? sql : sql.substring(0, spaceIndex));
    span.tag("sql.query", sql);
    span.remoteServiceName("mysql");
    span.remoteIpAndPort(mysql.getHost(), mysql.getPort());
    span.start();
  }

  @Override public void executeEnd(ExecuteContext ctx) {
    Span span = ThreadLocalSpan.CURRENT_TRACER.remove();
    if (span == null || span.isNoop()) return;
    if (ctx.sqlException() != null) span.error(ctx.sqlException());
    span.finish();
  }
}
