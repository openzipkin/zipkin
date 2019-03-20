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
package zipkin2.elasticsearch;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.squareup.moshi.JsonReader;
import okio.Buffer;
import okio.BufferedSource;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.elasticsearch.internal.client.JsonResponseConverter;
import zipkin2.elasticsearch.internal.client.RequestBuilder;
import zipkin2.elasticsearch.internal.client.ResponseConverter;
import zipkin2.internal.Nullable;
import zipkin2.internal.Platform;
import zipkin2.storage.AutocompleteTags;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static zipkin2.elasticsearch.ElasticsearchAutocompleteTags.AUTOCOMPLETE;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.DEPENDENCY;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.SPAN;
import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

@AutoValue
public abstract class ElasticsearchStorage extends zipkin2.storage.StorageComponent {

  /**
   * A list of elasticsearch nodes to connect to, in http://host:port or https://host:port format.
   * Note this value is only read once.
   */
  public interface HostsSupplier {
    List<String> get();
  }

  public static Builder newBuilder() {
    return new $AutoValue_ElasticsearchStorage.Builder()
        .hosts(Collections.singletonList("http://localhost:9200"))
        .maxRequests(64)
        .connectTimeout(1000)
        .strictTraceId(true)
        .searchEnabled(true)
        .index("zipkin")
        .dateSeparator('-')
        .indexShards(5)
        .indexReplicas(1)
        .namesLookback(86400000)
        .flushOnWrites(false)
        .autocompleteKeys(Collections.emptyList())
        .autocompleteTtl((int) TimeUnit.HOURS.toMillis(1))
        .autocompleteCardinality(5 * 4000); // Ex. 5 site tags with cardinality 4000 each
  }

  abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder extends StorageComponent.Builder {

    /**
     * A list of elasticsearch nodes to connect to, in http://host:port or https://host:port format.
     * Defaults to "http://localhost:9200".
     */
    public final Builder hosts(final List<String> hosts) {
      if (hosts == null) throw new NullPointerException("hosts == null");
      return hostsSupplier(
          new HostsSupplier() {
            @Override
            public List<String> get() {
              return hosts;
            }

            @Override
            public String toString() {
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

    /**
     * Basic HTTP Authentication provider
     */
    public final Builder credentialsProvider(String username, String password) {
      UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(AuthScope.ANY, credentials);
      return credentialsProvider(credentialsProvider);
    }

    /**
     * HTTP Authentication provider
     */
    public abstract Builder credentialsProvider(CredentialsProvider credentialsProvider);

    /**
     * Customize underlying Commons HTTP Client settings
     */
    public abstract Builder httpClientCustomizer(RestClientBuilder.HttpClientConfigCallback httpClientCustomizer);

    /**
     * Sets maximum in-flight requests from this process to any Elasticsearch host. Defaults to 64
     *
     * <p>A backlog is not permitted. Once this number of requests are in-flight, future requests
     * will drop until we are under maxRequests again. This allows the server to remain up during a
     * traffic surge.
     */
    public abstract Builder maxRequests(int maxRequests);

    /**
     * Socket connection timeout in ms, defaults to 1s
     */
    public abstract Builder connectTimeout(int connectTimeout);

    /**
     * Only valid when the destination is Elasticsearch 5.x. Indicates the ingest pipeline used
     * before spans are indexed. No default.
     *
     * <p>See https://www.elastic.co/guide/en/elasticsearch/reference/master/pipeline.html
     */
    public abstract Builder pipeline(String pipeline);

    /**
     * Only return span and service names where all {@link zipkin2.Span#timestamp()} are at or after
     * (now - lookback) in milliseconds. Defaults to 1 day (86400000).
     */
    public abstract Builder namesLookback(int namesLookback);

    /** Visible for testing */
    public abstract Builder flushOnWrites(boolean flushOnWrites);

    /** The index prefix to use when generating daily index names. Defaults to zipkin. */
    public final Builder index(String index) {
      indexNameFormatterBuilder().index(index);
      return this;
    }

    /**
     * The date separator to use when generating daily index names. Defaults to '-'.
     *
     * <p>By default, spans with a timestamp falling on 2016/03/19 end up in the index
     * 'zipkin:span-2016-03-19'. When the date separator is '.', the index would be
     * 'zipkin:span-2016.03.19'. If the date separator is 0, there is no delimiter. Ex the index
     * would be 'zipkin:span-20160319'
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
     * <p>Corresponds to <a
     * href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html">index.number_of_shards</a>
     */
    public abstract Builder indexShards(int indexShards);

    /**
     * The number of replica copies of each shard in the index. Each shard and its replicas are
     * assigned to a machine in the cluster. Increasing the number of replicas and machines in the
     * cluster will improve read performance, but not write performance. Number of replicas can be
     * changed for existing indices. Defaults to 1. It is highly discouraged to set this to 0 as it
     * would mean a machine failure results in data loss.
     *
     * <p>Corresponds to <a
     * href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html">index.number_of_replicas</a>
     */
    public abstract Builder indexReplicas(int indexReplicas);

    @Override
    public abstract Builder strictTraceId(boolean strictTraceId);

    @Override
    public abstract Builder searchEnabled(boolean searchEnabled);

    /** {@inheritDoc} */
    @Override
    public abstract Builder autocompleteKeys(List<String> autocompleteKeys);

    /** {@inheritDoc} */
    @Override
    public abstract Builder autocompleteTtl(int autocompleteTtl);

    /** {@inheritDoc} */
    @Override
    public abstract Builder autocompleteCardinality(int autocompleteCardinality);

    @Override
    public abstract ElasticsearchStorage build();

    abstract IndexNameFormatter.Builder indexNameFormatterBuilder();

    Builder() {}
  }

  public abstract HostsSupplier hostsSupplier();

  @Nullable
  public abstract RestClientBuilder.HttpClientConfigCallback httpClientCustomizer();

  @Nullable
  public abstract String pipeline();

  public abstract boolean flushOnWrites();

  public abstract int maxRequests();

  public abstract int connectTimeout();

  @Nullable
  public abstract CredentialsProvider credentialsProvider();

  public abstract boolean strictTraceId();

  abstract boolean searchEnabled();

  abstract List<String> autocompleteKeys();

  abstract int autocompleteTtl();

  abstract int autocompleteCardinality();

  abstract int indexShards();

  abstract int indexReplicas();

  public abstract IndexNameFormatter indexNameFormatter();

  public abstract int namesLookback();

  @Override
  public SpanStore spanStore() {
    ensureIndexTemplates();
    return new ElasticsearchSpanStore(this);
  }

  @Override
  public AutocompleteTags autocompleteTags() {
    ensureIndexTemplates();
    return new ElasticsearchAutocompleteTags(this);
  }

  @Override
  public SpanConsumer spanConsumer() {
    ensureIndexTemplates();
    return new ElasticsearchSpanConsumer(this);
  }

  /** Returns the Elasticsearch version of the connected cluster. Internal use only */
  public float version() {
    return ensureIndexTemplates().version();
  }

  /** This is a blocking call, only used in tests. */
  public void clear() throws IOException {
    Set<String> toClear = new LinkedHashSet<>();
    toClear.add(indexNameFormatter().formatType(SPAN));
    toClear.add(indexNameFormatter().formatType(DEPENDENCY));
    for (String index : toClear) clear(index);
  }

  void clear(String index) throws IOException {
    Request deleteRequest =
        RequestBuilder.delete(index)
          .tag("delete-index")
            .build();

    client().performRequest(deleteRequest);

    flush(client(), index);
  }

  /** This is a blocking call, only used in tests. */
  public static void flush(RestClient client, String index) throws IOException {
    Request flushRequest =
        RequestBuilder.post(index, "_flush")
            .tag("flush-index")
            .build();

    client.performRequest(flushRequest);
  }

  /** This is blocking so that we can determine if the cluster is healthy or not */
  @Override
  public CheckResult check() {
    return ensureClusterReady(indexNameFormatter().formatType(SPAN));
  }

  CheckResult ensureClusterReady(String index) {
    Request request =
        RequestBuilder.get("_cluster", "health", index)
            .tag("get-cluster-health")
            .build();

    try {
      return READ_STATUS.convert(client().performRequest(request));
    } catch (IOException | RuntimeException e) {
      return CheckResult.failed(e);
    }
  }

  private static final ResponseConverter<CheckResult> READ_STATUS = new JsonResponseConverter<CheckResult>() {
    @Override
    public CheckResult read(BufferedSource b) throws IOException {
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
    }
  };

  @Memoized // since we don't want overlapping calls to apply the index templates
  IndexTemplates ensureIndexTemplates() {
    String index = indexNameFormatter().index();
    try {
      IndexTemplates templates = new VersionSpecificTemplates(this).get(client());
      EnsureIndexTemplate.apply(client(), index + ":" + SPAN + "_template", templates.span());
      EnsureIndexTemplate.apply(
          client(), index + ":" + DEPENDENCY + "_template", templates.dependency());
      EnsureIndexTemplate.apply(
        client(), index + ":" + AUTOCOMPLETE + "_template", templates.autocomplete());
      return templates;
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  private static HttpHost parseHost(String host) {
    try {
      URL url = new URL(host.trim());
      return new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid Elasticsearch host " + host, e);
    }
  }

  @Memoized
  public RestClient client() {
    List<String> hosts = hostsSupplier().get();
    if (hosts.isEmpty()) throw new IllegalArgumentException("no hosts configured");
    HttpHost[] httpHosts= hosts.stream()
      .map(ElasticsearchStorage::parseHost)
      .toArray(s -> new HttpHost[s]);
    RestClientBuilder restClientBuilder = RestClient.builder(httpHosts)
      .setHttpClientConfigCallback(this::customizeHttpClient)
      .setRequestConfigCallback(this::customizeRequestConfig);
    return restClientBuilder.build();
  }

  private HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
    // TODO Fix mapping max requests -> max conn total
    httpClientBuilder.setMaxConnTotal(maxRequests());
    if (credentialsProvider() != null) {
      httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider());
    }
    if (httpClientCustomizer() != null) {
      httpClientCustomizer().customizeHttpClient(httpClientBuilder);
    }
    return httpClientBuilder;
  }

  private  RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
    if (connectTimeout()>0) requestConfigBuilder.setConnectTimeout(connectTimeout());
    return requestConfigBuilder;
  }

  @Override
  public void close() {
    try {
      client().close();
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  ElasticsearchStorage() {}
}
