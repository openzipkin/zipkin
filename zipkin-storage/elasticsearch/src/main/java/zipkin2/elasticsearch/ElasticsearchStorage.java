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

import com.fasterxml.jackson.core.JsonParser;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.client.ClientOptionsBuilder;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.HttpClientBuilder;
import com.linecorp.armeria.client.encoding.HttpDecodingClient;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.endpoint.EndpointGroupRegistry;
import com.linecorp.armeria.client.endpoint.EndpointSelectionStrategy;
import com.linecorp.armeria.client.endpoint.StaticEndpointGroup;
import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;
import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroupBuilder;
import com.linecorp.armeria.client.endpoint.healthcheck.HttpHealthCheckedEndpointGroup;
import com.linecorp.armeria.client.endpoint.healthcheck.HttpHealthCheckedEndpointGroupBuilder;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.Flags;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.util.AbstractListenable;
import com.linecorp.armeria.common.util.EventLoopGroups;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.elasticsearch.internal.JsonSerializers;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.internal.Nullable;
import zipkin2.internal.Platform;
import zipkin2.storage.AutocompleteTags;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

import static zipkin2.elasticsearch.ElasticsearchAutocompleteTags.AUTOCOMPLETE;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.DEPENDENCY;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.SPAN;
import static zipkin2.elasticsearch.EnsureIndexTemplate.ensureIndexTemplate;
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
      .clientCustomizer(unused -> {
      })
      .clientFactoryCustomizer(unused -> {
      })
      .hosts(Collections.singletonList("http://localhost:9200"))
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
     * Customizes the {@link HttpClientBuilder} used when connecting to ElasticSearch. This is used
     * by the server and tests to enable detailed logging and tweak timeouts.
     */
    public abstract Builder clientCustomizer(Consumer<ClientOptionsBuilder> clientCustomizer);

    /**
     * Customizes the {@link ClientFactoryBuilder} used when connecting to ElasticSearch. This is
     * used by the server and tests to tweak timeouts.
     */
    public abstract Builder clientFactoryCustomizer(
      Consumer<ClientFactoryBuilder> clientFactoryCustomizer);

    /**
     * A list of elasticsearch nodes to connect to, in http://host:port or https://host:port format.
     * Defaults to "http://localhost:9200".
     */
    public final Builder hosts(final List<String> hosts) {
      if (hosts == null) throw new NullPointerException("hosts == null");
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
     * <p>This was added to support dynamic endpoint resolution for Amazon Elasticsearch. This
     * value is only read once.
     */
    public abstract Builder hostsSupplier(HostsSupplier hosts);

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

    /**
     * Internal and visible only for testing.
     *
     * <p>See https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-refresh.html
     */
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
     * 'zipkin-span-2016-03-19'. When the date separator is '.', the index would be
     * 'zipkin-span-2016.03.19'. If the date separator is 0, there is no delimiter. Ex the index
     * would be 'zipkin-span-20160319'
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

    Builder() {
    }
  }

  abstract Consumer<ClientOptionsBuilder> clientCustomizer();

  abstract Consumer<ClientFactoryBuilder> clientFactoryCustomizer();

  public abstract HostsSupplier hostsSupplier();

  @Nullable public abstract String pipeline();

  public abstract boolean flushOnWrites();

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
  public ServiceAndSpanNames serviceAndSpanNames() {
    return (ServiceAndSpanNames) spanStore();
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

  char indexTypeDelimiter() {
    return ensureIndexTemplates().indexTypeDelimiter();
  }

  /** This is an internal blocking call, only used in tests. */
  public void clear() throws IOException {
    Set<String> toClear = new LinkedHashSet<>();
    toClear.add(indexNameFormatter().formatType(SPAN));
    toClear.add(indexNameFormatter().formatType(DEPENDENCY));
    for (String index : toClear) clear(index);
  }

  void clear(String index) throws IOException {
    String url = '/' + index;
    AggregatedHttpRequest delete = AggregatedHttpRequest.of(HttpMethod.DELETE, url);
    http().newCall(delete, BodyConverters.NULL).execute();
  }

  /** This is blocking so that we can determine if the cluster is healthy or not */
  @Override
  public CheckResult check() {
    HttpClient client = httpClient();
    EndpointGroup healthChecked = EndpointGroupRegistry.get("elasticsearch");
    if (healthChecked instanceof HttpHealthCheckedEndpointGroup) {
      try {
        ((HttpHealthCheckedEndpointGroup) healthChecked).awaitInitialEndpoints(
          client.options().responseTimeoutMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException | TimeoutException e) {
        return CheckResult.failed(e);
      }
    }
    return ensureClusterReady(indexNameFormatter().formatType(SPAN));
  }

  @Override public void close() {
    EndpointGroup endpointGroup = EndpointGroupRegistry.get("elasticsearch");
    if (endpointGroup != null) {
      endpointGroup.close();
      EndpointGroupRegistry.unregister("elasticsearch");
    }
    clientFactory().close();
  }

  CheckResult ensureClusterReady(String index) {
    try {
      HttpCall.Factory http = http();
      AggregatedHttpRequest request = AggregatedHttpRequest.of(
        HttpMethod.GET, "/_cluster/health/" + index);
      return http.newCall(request, ReadStatus.INSTANCE).execute();
    } catch (IOException | RuntimeException e) {
      if (e instanceof CompletionException) return CheckResult.failed(e.getCause());
      return CheckResult.failed(e);
    }
  }

  enum ReadStatus implements HttpCall.BodyConverter<CheckResult> {
    INSTANCE;

    @Override
    public CheckResult convert(ByteBuffer buf) throws IOException {
      ByteBuffer body = buf.duplicate();
      JsonParser status = enterPath(JsonSerializers.jsonParser(buf), "status");
      if (status == null) {
        throw new IllegalStateException("Health status couldn't be read " +
          StandardCharsets.UTF_8.decode(body).toString());
      }
      if ("RED".equalsIgnoreCase(status.getText())) {
        throw new IllegalStateException("Health status is RED");
      }
      return CheckResult.OK;
    }

    @Override
    public String toString() {
      return "ReadStatus";
    }
  }

  @Memoized // since we don't want overlapping calls to apply the index templates
  IndexTemplates ensureIndexTemplates() {
    try {
      IndexTemplates templates = new VersionSpecificTemplates(this).get();
      HttpCall.Factory http = http();
      ensureIndexTemplate(http, buildUrl(templates, SPAN), templates.span());
      ensureIndexTemplate(http, buildUrl(templates, DEPENDENCY), templates.dependency());
      ensureIndexTemplate(http, buildUrl(templates, AUTOCOMPLETE), templates.autocomplete());
      return templates;
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  String buildUrl(IndexTemplates templates, String type) {
    String indexPrefix = indexNameFormatter().index() + templates.indexTypeDelimiter();
    return "/_template/" + indexPrefix + type + "_template";
  }

  @Memoized // a new client factory means new connections
  ClientFactory clientFactory() {
    ClientFactoryBuilder builder = new ClientFactoryBuilder()
      // TODO(anuraaga): Remove after https://github.com/line/armeria/pull/1899
      .workerGroup(EventLoopGroups.newEventLoopGroup(
        Flags.numCommonWorkers(), "armeria-common-worker", true), true)
      .useHttp2Preface(false);
    clientFactoryCustomizer().accept(builder);
    return builder.build();
  }

  @Memoized // hosts resolution might imply a network call, and we might make a new client instance
  public HttpClient httpClient() {
    List<String> hosts = hostsSupplier().get();
    if (hosts.isEmpty()) throw new IllegalArgumentException("no hosts configured");

    List<URL> urls = hosts.stream()
      .map(host -> {
        try {
          return new URL(host);
        } catch (MalformedURLException e) {
          throw new IllegalArgumentException("Invalid host: " + host, e);
        }
      })
      .collect(Collectors.toList());

    final EndpointGroup endpointGroup;
    if (urls.size() == 1) {
      URL url = urls.get(0);
      if (isIpAddress(url.getHost())) {
        endpointGroup = null;
      } else {
        // A host that isn't an IP may resolve to multiple IP addresses, so we use a endpoint group
        // to round-robin over them.
        DnsAddressEndpointGroupBuilder dnsEndpoint =
          new DnsAddressEndpointGroupBuilder(url.getHost());
        if (url.getPort() != -1) {
          dnsEndpoint.port(url.getPort());
        }
        endpointGroup = dnsEndpoint.build();
      }
    } else {
      List<EndpointGroup> endpointGroups = new ArrayList<>();
      List<Endpoint> staticEndpoints = new ArrayList<>();
      for (URL url : urls) {
        if (isIpAddress(url.getHost())) {
          staticEndpoints.add(Endpoint.parse(url.getAuthority()));
        } else {
          // A host that isn't an IP may resolve to multiple IP addresses, so we use a endpoint
          // group to round-robin over them. Users can mix addresses that resolve to multiple IPs
          // with single IPs freely, they'll all get used.
          endpointGroups.add(url.getPort() == -1
            ? DnsAddressEndpointGroup.of(url.getHost())
            : DnsAddressEndpointGroup.of(url.getHost(), url.getPort()));
        }
      }

      if (!staticEndpoints.isEmpty()) {
        endpointGroups.add(new StaticEndpointGroup(staticEndpoints));
      }

      if (endpointGroups.size() == 1) {
        endpointGroup = endpointGroups.get(0);
      } else {
        endpointGroup = new CompositeEndpointGroup(endpointGroups);
      }
    }

    final String clientUrl;
    if (endpointGroup != null) {
      HttpHealthCheckedEndpointGroup healthChecked = new HttpHealthCheckedEndpointGroupBuilder(
        endpointGroup, "/_cluster/health")
        .protocol(SessionProtocol.valueOf(urls.get(0).getProtocol().toUpperCase(Locale.ROOT)))
        .clientFactory(clientFactory())
        .withClientOptions(options -> {
          clientCustomizer().accept(options);
          return options;
        })
        .build();
      EndpointGroupRegistry.register(
        "elasticsearch", healthChecked, EndpointSelectionStrategy.ROUND_ROBIN);
      clientUrl = urls.get(0).getProtocol() + "://group:elasticsearch" + urls.get(0).getPath();
    } else {
      // Just one non-domain URL, can connect directly without enabling load balancing.
      clientUrl = hosts.get(0);
    }

    ClientOptionsBuilder options = new ClientOptionsBuilder()
      .decorator(HttpDecodingClient.newDecorator());
    clientCustomizer().accept(options);
    HttpClientBuilder client = new HttpClientBuilder(clientUrl)
      .factory(clientFactory())
      .options(options.build());

    return client.build();
  }

  @Override public final String toString() {
    return "ElasticsearchStorage{hosts=" + hostsSupplier().get()
      + ", index=" + indexNameFormatter().index() + "}";
  }

  static boolean isIpAddress(String address) {
    return zipkin2.Endpoint.newBuilder().parseIp(address);
  }

  // TODO(anuraaga): Move this upstream - https://github.com/line/armeria/issues/1897
  static class CompositeEndpointGroup
    extends AbstractListenable<List<Endpoint>> implements EndpointGroup {

    final List<EndpointGroup> endpointGroups;

    CompositeEndpointGroup(List<EndpointGroup> endpointGroups) {
      this.endpointGroups = endpointGroups;
      for (EndpointGroup group : endpointGroups) {
        group.addListener(unused -> notifyListeners(endpoints()));
      }
    }

    @Override public List<Endpoint> endpoints() {
      List<Endpoint> merged = new ArrayList<>();
      for (EndpointGroup group : endpointGroups) {
        merged.addAll(group.endpoints());
      }
      return merged;
    }
  }

  @Memoized // hosts resolution might imply a network call, and we might make a new client instance
  public HttpCall.Factory http() {
    return new HttpCall.Factory(httpClient());
  }

  ElasticsearchStorage() {
  }
}
