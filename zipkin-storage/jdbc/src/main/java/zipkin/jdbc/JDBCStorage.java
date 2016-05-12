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
package zipkin.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.jooq.ExecuteListenerProvider;
import org.jooq.conf.Settings;
import zipkin.AsyncSpanConsumer;
import zipkin.AsyncSpanStore;
import zipkin.SpanStore;
import zipkin.StorageComponent;
import zipkin.internal.Nullable;

import static zipkin.StorageAdapters.blockingToAsync;
import static zipkin.internal.Util.checkNotNull;
import static zipkin.jdbc.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin.jdbc.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

public final class JDBCStorage implements StorageComponent {

  public static Builder builder() {
    return new Builder();
  }

  public final static class Builder {
    private DataSource datasource;
    private Settings settings = new Settings().withRenderSchema(false);
    private ExecuteListenerProvider listenerProvider;
    private Executor executor;

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

    public JDBCStorage build() {
      return new JDBCStorage(this);
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
  private final SpanStore spanStore;
  private final AsyncSpanStore asyncSpanStore;
  private final AsyncSpanConsumer asyncSpanConsumer;

  JDBCStorage(JDBCStorage.Builder builder) {
    this.datasource = builder.datasource;
    this.executor = builder.executor;
    this.context = new DSLContexts(builder.settings, builder.listenerProvider);
    this.spanStore = new JDBCSpanStore(datasource, context);
    this.asyncSpanStore = blockingToAsync(spanStore, executor);
    this.asyncSpanConsumer = blockingToAsync(new JDBCSpanConsumer(datasource, context), executor);
  }

  @Override public SpanStore spanStore() {
    return spanStore;
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    return asyncSpanStore;
  }

  @Override public AsyncSpanConsumer asyncSpanConsumer() {
    return asyncSpanConsumer;
  }

  @Override public void close() {
    // didn't open the DataSource or executor
  }

  /** Visible for testing */
  void clear() {
    try (Connection conn = datasource.getConnection()) {
      context.get(conn).truncate(ZIPKIN_SPANS).execute();
      context.get(conn).truncate(ZIPKIN_ANNOTATIONS).execute();
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }
}
