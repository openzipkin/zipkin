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
package zipkin.storage.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.jooq.ExecuteListenerProvider;
import org.jooq.conf.Settings;
import zipkin.internal.Lazy;
import zipkin.internal.Nullable;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;

import static zipkin.internal.Util.checkNotNull;
import static zipkin.storage.StorageAdapters.blockingToAsync;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

public final class MySQLStorage implements StorageComponent {
  public static Builder builder() {
    return new Builder();
  }

  public final static class Builder implements StorageComponent.Builder {
    boolean strictTraceId = true;
    private DataSource datasource;
    private Settings settings = new Settings().withRenderSchema(false);
    private ExecuteListenerProvider listenerProvider;
    private Executor executor;

    /** {@inheritDoc} */
    @Override public Builder strictTraceId(boolean strictTraceId) {
      this.strictTraceId = strictTraceId;
      return this;
    }

    public Builder datasource(DataSource datasource) {
      this.datasource = checkNotNull(datasource, "datasource");
      return this;
    }

    public Builder settings(Settings settings) {
      this.settings = checkNotNull(settings, "settings");
      return this;
    }

    public Builder listenerProvider(@Nullable ExecuteListenerProvider listenerProvider) {
      this.listenerProvider = listenerProvider;
      return this;
    }

    public Builder executor(Executor executor) {
      this.executor = checkNotNull(executor, "executor");
      return this;
    }

    @Override public MySQLStorage build() {
      return new MySQLStorage(this);
    }

    Builder() {
    }
  }

  static {
    System.setProperty("org.jooq.no-logo", "true");
  }

  private final DataSource datasource;
  private final Executor executor;
  private final DSLContexts context;
  final Lazy<Schema> schema;
  final boolean strictTraceId;

  MySQLStorage(MySQLStorage.Builder builder) {
    this.datasource = checkNotNull(builder.datasource, "datasource");
    this.executor = checkNotNull(builder.executor, "executor");
    this.context = new DSLContexts(builder.settings, builder.listenerProvider);
    this.strictTraceId = builder.strictTraceId;
    this.schema = new Lazy<Schema>() {
      @Override protected Schema compute() {
        return new Schema(datasource, context, strictTraceId);
      }
    };
  }

  /** Returns the session in use by this storage component. */
  public DataSource datasource() {
    return datasource;
  }

  @Override public SpanStore spanStore() {
    return new MySQLSpanStore(datasource, context, schema.get(), strictTraceId);
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    return blockingToAsync(spanStore(), executor);
  }

  @Override public AsyncSpanConsumer asyncSpanConsumer() {
    MySQLSpanConsumer spanConsumer = new MySQLSpanConsumer(datasource, context, schema.get());
    return blockingToAsync(spanConsumer, executor);
  }

  @Override public CheckResult check() {
    try (Connection conn = datasource.getConnection()) {
      context.get(conn).select(ZIPKIN_SPANS.TRACE_ID).from(ZIPKIN_SPANS).limit(1).execute();
    } catch (SQLException | RuntimeException e) {
      return CheckResult.failed(e);
    }
    return CheckResult.OK;
  }

  @Override public void close() {
    // didn't open the DataSource or executor
  }

  /** Visible for testing */
  void clear() {
    try (Connection conn = datasource.getConnection()) {
      context.get(conn).truncate(ZIPKIN_SPANS).execute();
      context.get(conn).truncate(ZIPKIN_ANNOTATIONS).execute();
      context.get(conn).truncate(ZIPKIN_DEPENDENCIES).execute();
    } catch (SQLException | RuntimeException e) {
      throw new AssertionError(e);
    }
  }
}
