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
package zipkin.storage.elasticsearch.http;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.squareup.moshi.JsonReader;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import zipkin.internal.AsyncSpan2ConsumerAdapter;
import zipkin.internal.Nullable;
import zipkin.internal.Span2Component;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageAdapters;
import zipkin.storage.StorageComponent;
import zipkin.storage.elasticsearch.http.internal.LenientDoubleCallbackAsyncSpanStore;
import zipkin.storage.elasticsearch.http.internal.client.HttpCall;

import static zipkin.internal.Util.checkNotNull;
import static zipkin.moshi.JsonReaders.enterPath;
import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanStore.DEPENDENCY;
import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanStore.SPAN;

@AutoValue
public abstract class ElasticsearchHttpStorage extends Span2Component implements StorageComponent {
  /**
   * A list of elasticsearch nodes to connect to, in http://host:port or https://host:port
   * format. Note this value is only read once.
   */
  public interface HostsSupplier {
    List<String> get();
  }

  static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

  public static Builder builder(OkHttpClient client) {
    return new $AutoValue_ElasticsearchHttpStorage.Builder()
        .client(client)
        .hosts(Collections.singletonList("http://localhost:9200"))
        .maxRequests(64)
        .strictTraceId(true)
        .index("zipkin")
        .dateSeparator('-')
        .indexShards(5)
        .indexReplicas(1)
        .namesLookback(86400000)
        .shutdownClientOnClose(false)
        .flushOnWrites(false)
        .legacyReadsEnabled(true);
  }

  public static Builder builder() {
    Builder result = builder(new OkHttpClient());
    result.shutdownClientOnClose(true);
    return result;
  }

  @AutoValue.Builder
  public static abstract class Builder implements StorageComponent.Builder {
    abstract Builder client(OkHttpClient client);

    abstract Builder shutdownClientOnClose(boolean shutdownClientOnClose);

    /**
     * A list of elasticsearch nodes to connect to, in http://host:port or https://host:port
     * format. Defaults to "http://localhost:9200".
     */
    public final Builder hosts(final List<String> hosts) {
      checkNotNull(hosts, "hosts");
      return hostsSupplier(new HostsSupplier() {
        @Override public List<String> get() {
          return hosts;
        }

        @Override public String toString() {
          return hosts.toString();
        }
      });
    }

    /**
     * Like {@link #hosts(List)}, except the value is deferred.
     *
     * <p>This was added to support dynamic endpoint resolution for Amazon Elasticsearch. This value
     * is only read once.
     */
    public abstract Builder hostsSupplier(HostsSupplier hosts);

    /** Sets maximum in-flight requests from this process to any Elasticsearch host. Defaults to 64 */
    public abstract Builder maxRequests(int maxRequests);

    /**
     * Only valid when the destination is Elasticsearch 5.x. Indicates the ingest pipeline used
     * before spans are indexed. No default.
     *
     * <p>See https://www.elastic.co/guide/en/elasticsearch/reference/master/pipeline.html
     */
    public abstract Builder pipeline(String pipeline);

    /**
     * Only return span and service names where all {@link zipkin.Span#timestamp} are at or after
     * (now - lookback) in milliseconds. Defaults to 1 day (86400000).
     */
    public abstract Builder namesLookback(int namesLookback);

    /** When true, Redundantly queries indexes made with pre v1.31 collectors. Defaults to true. */
    public abstract Builder legacyReadsEnabled(boolean legacyReadsEnabled);

    /** Visible for testing */
    abstract Builder flushOnWrites(boolean flushOnWrites);

    /**
     * The index prefix to use when generating daily index names. Defaults to zipkin.
     */
    public final Builder index(String index) {
      indexNameFormatterBuilder().index(index);
      return this;
    }

    /**
     * The date separator to use when generating daily index names. Defaults to '-'.
     *
     * <p>By default, spans with a timestamp falling on 2016/03/19 end up in the index
     * 'zipkin:span-2016-03-19'. When the date separator is '.', the index would be
     * 'zipkin:span-2016.03.19'.
     */
    public final Builder dateSeparator(char dateSeparator) {
      indexNameFormatterBuilder().dateSeparator(dateSeparator);
      return this;
    }

    /**
     * The number of shards to split the index into. Each shard and its replicas are assigned to a
     * machine in the cluster. Increasing the number of shards and machines in the cluster will
     * improve read and write performance. Number of shards cannot be changed for existing indices,
     * but new daily indices will pick up changes to the setting. Defaults to 5.
     *
     * <p>Corresponds to <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html">index.number_of_shards</a>
     */
    public abstract Builder indexShards(int indexShards);

    /**
     * The number of replica copies of each shard in the index. Each shard and its replicas are
     * assigned to a machine in the cluster. Increasing the number of replicas and machines in the
     * cluster will improve read performance, but not write performance. Number of replicas can be
     * changed for existing indices. Defaults to 1. It is highly discouraged to set this to 0 as it
     * would mean a machine failure results in data loss.
     *
     * <p>Corresponds to <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html">index.number_of_replicas</a>
     */
    public abstract Builder indexReplicas(int indexReplicas);

    @Override public abstract Builder strictTraceId(boolean strictTraceId);

    @Override public abstract ElasticsearchHttpStorage build();

    abstract IndexNameFormatter.Builder indexNameFormatterBuilder();

    Builder() {
    }
  }

  abstract OkHttpClient client();

  abstract boolean shutdownClientOnClose();

  abstract HostsSupplier hostsSupplier();

  @Nullable abstract String pipeline();

  abstract boolean flushOnWrites();

  abstract int maxRequests();

  abstract boolean strictTraceId();

  abstract int indexShards();

  abstract int indexReplicas();

  abstract IndexNameFormatter indexNameFormatter();

  abstract int namesLookback();

  abstract boolean legacyReadsEnabled();

  @Override public SpanStore spanStore() {
    return StorageAdapters.asyncToBlocking(asyncSpanStore());
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    float version = ensureIndexTemplates().version();
    if (version >= 6 /* multi-type (legacy) index isn't possible */ || !legacyReadsEnabled()) {
      return new ElasticsearchHttpSpanStore(this);
    } else { // fan out queries as we don't know if old legacy collectors are in use
      return new LenientDoubleCallbackAsyncSpanStore(
        new ElasticsearchHttpSpanStore(this),
        new LegacyElasticsearchHttpSpanStore(this)
      );
    }
  }

  @Override public AsyncSpanConsumer asyncSpanConsumer() {
    return AsyncSpan2ConsumerAdapter.create(asyncSpan2Consumer());
  }

  @Override protected zipkin.internal.v2.storage.AsyncSpanConsumer asyncSpan2Consumer() {
    ensureIndexTemplates();
    return new ElasticsearchHttpSpanConsumer(this);
  }

  /** This is a blocking call, only used in tests. */
  void clear() throws IOException {
    Set<String> toClear = new LinkedHashSet<>();
    toClear.add(indexNameFormatter().formatType(SPAN));
    toClear.add(indexNameFormatter().formatType(DEPENDENCY));
    for (String index : toClear) clear(index);
  }

  void clear(String index) throws IOException {
    Request deleteRequest = new Request.Builder()
        .url(http().baseUrl.newBuilder().addPathSegment(index).build())
        .delete().tag("delete-index").build();

    http().execute(deleteRequest, b -> null);

    flush(http(), index);
  }

  /** This is a blocking call, only used in tests. */
  static void flush(HttpCall.Factory factory, String index) throws IOException {
    Request flushRequest = new Request.Builder()
        .url(factory.baseUrl.newBuilder().addPathSegment(index).addPathSegment("_flush").build())
        .post(RequestBody.create(APPLICATION_JSON, ""))
        .tag("flush-index").build();

    factory.execute(flushRequest, b -> null);
  }

  /** This is blocking so that we can determine if the cluster is healthy or not */
  @Override public CheckResult check() {
    return ensureClusterReady(indexNameFormatter().formatType(SPAN));
  }

  CheckResult ensureClusterReady(String index) {
    Request request = new Request.Builder().url(http().baseUrl.resolve("/_cluster/health/" + index))
        .tag("get-cluster-health").build();

    try {
      return http().execute(request, b -> {
        b.request(Long.MAX_VALUE); // Buffer the entire body.
        Buffer body = b.buffer();
        JsonReader status = enterPath(JsonReader.of(body.clone()), "status");
        if (status == null) {
          throw new IllegalStateException("Health status couldn't be read " + body.readUtf8());
        }
        if ("RED".equalsIgnoreCase(status.nextString())) {
          throw new IllegalStateException("Health status is RED");
        }
        return CheckResult.OK;
      });
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
  }

  @Memoized // since we don't want overlapping calls to apply the index templates
  IndexTemplates ensureIndexTemplates() {
    String index = indexNameFormatter().index();
    IndexTemplates templates = new VersionSpecificTemplates(this).get(http());
    EnsureIndexTemplate.apply(http(), index + ":" + SPAN + "_template", templates.span());
    EnsureIndexTemplate.apply(http(), index + ":" + DEPENDENCY + "_template",
      templates.dependency());
    return templates;
  }

  @Memoized // hosts resolution might imply a network call, and we might make a new okhttp instance
  HttpCall.Factory http() {
    List<String> hosts = hostsSupplier().get();
    if (hosts.isEmpty()) throw new IllegalArgumentException("no hosts configured");
    OkHttpClient ok = hosts.size() == 1
        ? client()
        : client().newBuilder()
            .dns(PseudoAddressRecordSet.create(hosts, client().dns()))
            .build();
    ok.dispatcher().setMaxRequests(maxRequests());
    ok.dispatcher().setMaxRequestsPerHost(maxRequests());
    return new HttpCall.Factory(ok, HttpUrl.parse(hosts.get(0)));
  }

  @Override public void close() {
    if (!shutdownClientOnClose()) return;
    http().close();
  }

  ElasticsearchHttpStorage() {
  }
}
