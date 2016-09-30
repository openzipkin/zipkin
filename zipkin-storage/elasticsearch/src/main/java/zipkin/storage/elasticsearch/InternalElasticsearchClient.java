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
package zipkin.storage.elasticsearch;

import com.google.common.util.concurrent.ListenableFuture;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Lazy;

import static com.google.common.base.Preconditions.checkNotNull;
import static zipkin.storage.elasticsearch.ElasticsearchSpanConsumer.prefixWithTimestampMillis;

/**
 * Common interface for different protocol implementations communicating with Elasticsearch.
 *
 * <p>Due to the difficulties of shoehorning auth over the native Elasticsearch protocol, many PaaS
 * providers (like AWS) only offer REST interfaces to ElasticSearch (presumably they meet their
 * security requirements with an HTTP proxy layer).
 *
 * <p>That means, in order to support both aaS elasticsearch and "native" elasticsearch, we require
 * a shim.
 */
public abstract class InternalElasticsearchClient implements Closeable {
  /**
   * The maximum count of raw spans returned in a trace query.
   *
   * <p>Not configurable as it implies adjustments to the index template (index.max_result_window)
   * and user settings
   *
   * <p> See https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html
   */
  protected static final int MAX_RAW_SPANS = 10000; // the default elasticsearch allowed limit
  protected static final String SPAN = "span";
  protected static final String DEPENDENCY_LINK = "dependencylink";

  public interface Factory {
    InternalElasticsearchClient create(String allIndices);
  }

  public static abstract class Builder {
    /** The elasticsearch cluster to connect to, defaults to "elasticsearch". */
    public abstract Builder cluster(String cluster);

    /**
     * A comma separated list of elasticsearch host to connect to, in a transport-specific format.
     * For example, for the native client, this would default to "localhost:9300".
     */
    public abstract Builder hosts(Lazy<List<String>> hosts);

    /**
     * Internal flag that allows you read-your-writes consistency during tests. With Elasticsearch,
     * it is not sufficient to block on futures since the index also needs to be flushed.
     */
    public abstract Builder flushOnWrites(boolean flushOnWrites);

    public final Builder hosts(final List<String> hosts) {
      checkNotNull(hosts, "hosts");
      return hosts(new Lazy<List<String>>() {
        @Override protected List<String> compute() {
          return hosts;
        }
      });
    }

    public abstract Factory buildFactory();
  }

  /** Ensures the existence of a template, creating it if it does not exist. */
  protected abstract void ensureTemplate(String name, String indexTemplate) throws IOException;

  /** Deletes the specified index pattern is supplied */
  protected abstract void clear(String index) throws IOException;

  /**
   * Scans the given indices with the given query and aggregates a sorted list of unique bucket
   * keys.
   *
   * <p>If aggregating all traces, pass {@link QueryBuilders#matchAllQuery()}
   *
   * <p>NB: This call is _lenient_ in its index selection, and only will match open indexes, cf.
   * {@link IndicesOptions#lenientExpandOpen()}.
   */
  protected abstract ListenableFuture<List<String>> collectBucketKeys(String[] indices,
      QueryBuilder query, AbstractAggregationBuilder... aggregations);

  /**
   * Retrieves spans from the given indices. Pass {@link QueryBuilders#matchAllQuery()} for all
   * spans.
   *
   * <p>NB: This call is _lenient_ in its index selection, and only will match open indexes, cf.
   * {@link IndicesOptions#lenientExpandOpen()}.
   */
  protected abstract ListenableFuture<List<Span>> findSpans(String[] indices, QueryBuilder query);

  /**
   * Retrieves all dependency links in the given indices. Useful for gathering dependencies from
   * a (time-bounded) set of "recent" indices.
   *
   * <p>NB: This call is _lenient_ in its index selection, and only will match open indexes, cf.
   * {@link IndicesOptions#lenientExpandOpen()} }.
   */
  protected abstract ListenableFuture<List<DependencyLink>> findDependencies(String[] indices);

  /**
   * Indexes the spans, flushing depending on the value of {@link Builder#flushOnWrites(boolean)}
   */
  protected abstract BulkSpanIndexer bulkSpanIndexer();

  public interface BulkSpanIndexer {
    /**
     * In order to allow systems like Kibana to search by timestamp, we add a field
     * "timestamp_millis" when storing a span that has a timestamp. The cheapest way to do this
     * without changing the codec is prefixing it to the json.
     *
     * <p>For example. {"traceId":".. becomes {"timestamp_millis":12345,"traceId":"...
     */
    void add(String index, Span span, Long timestampMillis) throws IOException;

    ListenableFuture<Void> execute() throws IOException;
  }

  protected static abstract class SpanBytesBulkSpanIndexer implements BulkSpanIndexer {

    @Override public final void add(String index, Span span, Long timestampMillis) {
      add(index, toSpanBytes(span, timestampMillis));
    }

    abstract protected void add(String index, byte[] spanBytes);
  }

  public static byte[] toSpanBytes(Span span, Long timestampMillis) {
    return timestampMillis != null
        ? prefixWithTimestampMillis(Codec.JSON.writeSpan(span), timestampMillis)
        : Codec.JSON.writeSpan(span);
  }

  /**
   * Throws an exception if the health status is red, or a connection couldn't be made.
   *
   * @param catchAll See {@link IndexNameFormatter#catchAll()}
   */
  protected abstract void ensureClusterReady(String catchAll) throws IOException;

  /** Overridden to remove checked exceptions. */
  @Override
  public abstract void close();
}
