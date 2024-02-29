/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.auth.AuthProvider;
import com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider;
import com.datastax.oss.driver.api.core.config.DriverOption;
import java.util.Map;
import java.util.Set;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.internal.ClosedComponentException;
import zipkin2.internal.Nullable;
import zipkin2.storage.AutocompleteTags;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.Traces;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

/**
 * CQL3 implementation of zipkin storage.
 *
 * <p>Queries are logged to the category "com.datastax.oss.driver.api.core.cql.QueryLogger" when
 * debug or trace is enabled via SLF4J. Trace level includes bound values.
 *
 * <p>Schema is installed by default from "/zipkin2-schema.cql"
 *
 * <p>When {@link StorageComponent.Builder#strictTraceId(boolean)} is disabled, span and index data
 * are uniformly written with 64-bit trace ID length. When retrieving data, an extra "trace_id_high"
 * field clarifies if a 128-bit trace ID was sent.
 */
public final class CassandraStorage extends StorageComponent {
  // @FunctionalInterface, except safe for lower language levels
  public interface SessionFactory {
    SessionFactory DEFAULT = new DefaultSessionFactory();

    CqlSession create(CassandraStorage storage);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder extends CassandraStorageBuilder<Builder> {
    Builder() {
      super(Schema.DEFAULT_KEYSPACE);
    }

    /** Keyspace to store span and index data. Defaults to "zipkin2" */
    @Override public Builder keyspace(String keyspace) {
      return super.keyspace(keyspace);
    }

    /**
     * Ensures that schema exists, if enabled tries to execute:
     * <ol>
     *   <li>io.zipkin.zipkin2:zipkin-storage-cassandra/zipkin2-schema.cql</li>
     *   <li>io.zipkin.zipkin2:zipkin-storage-cassandra/zipkin2-indexes.cql</li>
     * </ol>
     * Defaults to true.
     */
    @Override public Builder ensureSchema(boolean ensureSchema) {
      return super.ensureSchema(ensureSchema);
    }

    @Override public CassandraStorage build() {
      return new CassandraStorage(this);
    }
  }

  final boolean strictTraceId, searchEnabled;
  final Set<String> autocompleteKeys;
  final int autocompleteTtl, autocompleteCardinality;

  final String contactPoints, localDc;
  final Map<DriverOption, Integer> poolingOptions;
  @Nullable final AuthProvider authProvider;
  final boolean useSsl;
  final boolean sslHostnameValidation;
  final String keyspace;
  final int maxTraceCols, indexFetchMultiplier;

  final LazySession session;

  CassandraStorage(CassandraStorageBuilder<?> builder) {
    // Assign generic configuration for all storage components
    this.strictTraceId = builder.strictTraceId;
    this.searchEnabled = builder.searchEnabled;
    this.autocompleteKeys = builder.autocompleteKeys;
    this.autocompleteTtl = builder.autocompleteTtl;
    this.autocompleteCardinality = builder.autocompleteCardinality;

    // Assign configuration used to create a session
    this.contactPoints = builder.contactPoints;
    this.localDc = builder.localDc;
    this.poolingOptions = builder.poolingOptions();
    if (builder.username != null) {
      this.authProvider = new ProgrammaticPlainTextAuthProvider(builder.username, builder.password);
    } else {
      this.authProvider = null;
    }
    this.useSsl = builder.useSsl;
    this.sslHostnameValidation = builder.sslHostnameValidation;
    this.keyspace = builder.keyspace;

    // Assign configuration used to control queries
    this.maxTraceCols = builder.maxTraceCols;
    this.indexFetchMultiplier = builder.indexFetchMultiplier;

    this.session = new LazySession(this, builder.sessionFactory, builder.ensureSchema);
  }

  /** close is typically called from a different thread */
  volatile boolean closeCalled;

  volatile CassandraSpanConsumer spanConsumer;
  volatile CassandraSpanStore spanStore;
  volatile CassandraAutocompleteTags tagStore;

  /** Lazy initializes or returns the session in use by this storage component. */
  CqlSession session() {
    return session.get();
  }

  Schema.Metadata metadata() {
    return session.metadata();
  }

  /** {@inheritDoc} Memoized in order to avoid re-preparing statements */
  @Override public SpanStore spanStore() {
    if (spanStore == null) {
      synchronized (this) {
        if (spanStore == null) {
          spanStore = new CassandraSpanStore(this);
        }
      }
    }
    return spanStore;
  }

  @Override public Traces traces() {
    return (Traces) spanStore();
  }

  @Override public ServiceAndSpanNames serviceAndSpanNames() {
    return (ServiceAndSpanNames) spanStore();
  }

  @Override public AutocompleteTags autocompleteTags() {
    if (tagStore == null) {
      synchronized (this) {
        if (tagStore == null) {
          tagStore = new CassandraAutocompleteTags(this);
        }
      }
    }
    return tagStore;
  }

  // Memoized in order to avoid re-preparing statements
  @Override public SpanConsumer spanConsumer() {
    if (spanConsumer == null) {
      synchronized (this) {
        if (spanConsumer == null) {
          spanConsumer = new CassandraSpanConsumer(this);
        }
      }
    }
    return spanConsumer;
  }

  @Override public boolean isOverCapacity(Throwable e) {
    return ResultSetFutureCall.isOverCapacity(e);
  }

  @Override public String toString() {
    return "CassandraStorage{contactPoints=" + contactPoints + ", keyspace=" + keyspace + "}";
  }

  @Override public CheckResult check() {
    if (closeCalled) throw new ClosedComponentException();
    try {
      session.healthCheck();
    } catch (Throwable e) {
      Call.propagateIfFatal(e);
      return CheckResult.failed(e);
    }
    return CheckResult.OK;
  }

  @Override public void close() {
    if (closeCalled) return;
    session.close();
    closeCalled = true;
  }
}
