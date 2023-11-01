/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.storage.cassandra.dao.executor;

import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableHelper;
import zipkin2.Endpoint;
import zipkin2.Span;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class BaseQueryExecutor {
  public static final int NAME_QUERY_MAX_SIZE = Integer.MAX_VALUE;
  private static final Gson GSON = new Gson();

  private final CassandraClient client;

  public BaseQueryExecutor(CassandraClient client, CassandraTableHelper tableHelper) {
    this.client = client;
  }

  protected <T> Query<T> buildQuery(Supplier<String> cql, CassandraClient.ResultHandler<T> handler) {
    return new Query<>(client, cql, handler);
  }

  protected <T> List<T> executeSync(Query<T> query, Object... params) {
    return this.client.executeQuery(query.getStatement(), query.handler, params);
  }

  public <T> CompletionStage<List<T>> executeAsync(Query<T> query, Object... params) {
    return this.client.executeAsyncQuery(query.getStatement(), query.handler, params);
  }

  public <T, S extends Statement> CompletionStage<List<T>> executeAsyncWithCustomizeStatement(Query<T> query, Function<PreparedStatement, S> statement) {
    final PreparedStatement original = query.getStatement();
    final Statement stmt = statement.apply(original);
    return this.client.executeAsyncQueryWithCustomBind(original, stmt, query.handler);
  }

  protected Span buildSpan(Row row) {
    Span.Builder span = Span.newBuilder();
    span.traceId(row.getString(ZipkinSpanRecord.TRACE_ID));
    span.id(row.getString(ZipkinSpanRecord.SPAN_ID));
    span.parentId(row.getString(ZipkinSpanRecord.PARENT_ID));
    String kind = row.getString(ZipkinSpanRecord.KIND);
    if (!StringUtil.isEmpty(kind)) {
      span.kind(Span.Kind.valueOf(kind));
    }
    span.timestamp(row.getLong(ZipkinSpanRecord.TIMESTAMP));
    span.duration(row.getLong(ZipkinSpanRecord.DURATION));
    span.name(row.getString(ZipkinSpanRecord.NAME));

    if (row.getInt(ZipkinSpanRecord.DEBUG) > 0) {
      span.debug(Boolean.TRUE);
    }
    if (row.getInt(ZipkinSpanRecord.SHARED) > 0) {
      span.shared(Boolean.TRUE);
    }
    //Build localEndpoint
    Endpoint.Builder localEndpoint = Endpoint.newBuilder();
    localEndpoint.serviceName(row.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME));
    if (!StringUtil.isEmpty(row.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_IPV4))) {
      localEndpoint.parseIp(row.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_IPV4));
    } else {
      localEndpoint.parseIp(row.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_IPV6));
    }
    localEndpoint.port(row.getInt(ZipkinSpanRecord.LOCAL_ENDPOINT_PORT));
    span.localEndpoint(localEndpoint.build());
    //Build remoteEndpoint
    Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
    remoteEndpoint.serviceName(row.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME));
    if (!StringUtil.isEmpty(row.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_IPV4))) {
      remoteEndpoint.parseIp(row.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_IPV4));
    } else {
      remoteEndpoint.parseIp(row.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_IPV6));
    }
    remoteEndpoint.port(row.getInt(ZipkinSpanRecord.REMOTE_ENDPOINT_PORT));
    span.remoteEndpoint(remoteEndpoint.build());

    //Build tags
    String tagsString = row.getString(ZipkinSpanRecord.TAGS);
    if (!StringUtil.isEmpty(tagsString)) {
      JsonObject tagsJson = GSON.fromJson(tagsString, JsonObject.class);
      for (Map.Entry<String, JsonElement> tag : tagsJson.entrySet()) {
        span.putTag(tag.getKey(), tag.getValue().getAsString());
      }
    }
    //Build annotation
    String annotationString = row.getString(ZipkinSpanRecord.ANNOTATIONS);
    if (!StringUtil.isEmpty(annotationString)) {
      JsonObject annotationJson = GSON.fromJson(annotationString, JsonObject.class);
      for (Map.Entry<String, JsonElement> annotation : annotationJson.entrySet()) {
        span.addAnnotation(Long.parseLong(annotation.getKey()), annotation.getValue().getAsString());
      }
    }
    return span.build();
  }


  protected class Query<T> {
    private final Supplier<PreparedStatement> statementSupplier;
    private volatile PreparedStatement statement;
    private final CassandraClient.ResultHandler<T> handler;

    public Query(CassandraClient client, Supplier<String> query, CassandraClient.ResultHandler<T> handler) {
      this.statementSupplier = () -> client.prepare(query.get());
      this.handler = handler;
    }

    public PreparedStatement getStatement() {
      if (statement == null) {
        synchronized (this) {
          if (statement == null) {
            statement = statementSupplier.get();
          }
        }
      }
      return statement;
    }
  }

}
