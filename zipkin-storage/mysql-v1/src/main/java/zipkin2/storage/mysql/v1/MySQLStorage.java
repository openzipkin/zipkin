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
package zipkin2.storage.mysql.v1;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.jooq.ExecuteListenerProvider;
import org.jooq.conf.Settings;
import zipkin2.CheckResult;
import zipkin2.internal.Nullable;
import zipkin2.storage.AutocompleteTags;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

public final class MySQLStorage extends StorageComponent {
  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder extends StorageComponent.Builder {
    boolean strictTraceId = true, searchEnabled = true;
    private DataSource datasource;
    private Settings settings = new Settings().withRenderSchema(false);
    private ExecuteListenerProvider listenerProvider;
    private Executor executor;
    List<String> autocompleteKeys = new ArrayList<>();

    @Override public Builder strictTraceId(boolean strictTraceId) {
      this.strictTraceId = strictTraceId;
      return this;
    }

    @Override public Builder searchEnabled(boolean searchEnabled) {
      this.searchEnabled = searchEnabled;
      return this;
    }

    @Override public Builder autocompleteKeys(List<String> keys) {
      if (keys == null) throw new NullPointerException("keys == null");
      this.autocompleteKeys = keys;
      return this;
    }

    public Builder datasource(DataSource datasource) {
      if (datasource == null) throw new NullPointerException("datasource == null");
      this.datasource = datasource;
      return this;
    }

    public Builder settings(Settings settings) {
      if (settings == null) throw new NullPointerException("settings == null");
      this.settings = settings;
      return this;
    }

    public Builder listenerProvider(@Nullable ExecuteListenerProvider listenerProvider) {
      this.listenerProvider = listenerProvider;
      return this;
    }

    public Builder executor(Executor executor) {
      if (executor == null) throw new NullPointerException("executor == null");
      this.executor = executor;
      return this;
    }

    @Override public MySQLStorage build() {
      return new MySQLStorage(this);
    }

    Builder() {}
  }

  static {
    System.setProperty("org.jooq.no-logo", "true");
  }

  final DataSource datasource;
  final DataSourceCall.Factory dataSourceCallFactory;
  final DSLContexts context;
  final boolean strictTraceId, searchEnabled;
  final List<String> autocompleteKeys;
  volatile Schema schema;

  MySQLStorage(MySQLStorage.Builder builder) {
    datasource = builder.datasource;
    if (datasource == null) throw new NullPointerException("datasource == null");
    Executor executor = builder.executor;
    if (executor == null) throw new NullPointerException("executor == null");
    context = new DSLContexts(builder.settings, builder.listenerProvider);
    dataSourceCallFactory = new DataSourceCall.Factory(datasource, context, executor);
    strictTraceId = builder.strictTraceId;
    searchEnabled = builder.searchEnabled;
    autocompleteKeys = builder.autocompleteKeys;
  }

  /** Returns the session in use by this storage component. */
  public DataSource datasource() {
    return datasource;
  }

  /** Lazy to avoid eager I/O */
  Schema schema() {
    if (schema == null) {
      synchronized (this) {
        if (schema == null) {
          schema = new Schema(datasource, context, strictTraceId);
        }
      }
    }
    return schema;
  }

  @Override public SpanStore spanStore() {
    return new MySQLSpanStore(this, schema());
  }

  @Override public ServiceAndSpanNames serviceAndSpanNames() {
    return new MySQLSpanStore(this, schema());
  }

  @Override public AutocompleteTags autocompleteTags() {
    return new MySQLAutocompleteTags(this, schema());
  }

  @Override public SpanConsumer spanConsumer() {
    return new MySQLSpanConsumer(dataSourceCallFactory, schema());
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
