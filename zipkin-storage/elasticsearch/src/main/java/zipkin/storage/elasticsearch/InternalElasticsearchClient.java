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
import java.util.Collection;
import java.util.List;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import zipkin.DependencyLink;
import zipkin.Span;

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

  protected interface Factory {
    InternalElasticsearchClient create(String allIndices);
  }

  // abstract class so we can hide flushOnWrites
  protected interface Builder {
    /** The elasticsearch cluster to connect to, defaults to "elasticsearch". */
    Builder cluster(String cluster);

    /**
     * A comma separated list of elasticsearch host to connect to, in a transport-specific format.
     * For example, for the native client, this would default to "localhost:9300".
     */
    Builder hosts(List<String> hosts);

    /**
     * Internal flag that allows you read-your-writes consistency during tests. With Elasticsearch,
     * it is not sufficient to block on futures since the index also needs to be flushed.
     */
    Builder flushOnWrites(boolean flushOnWrites);

    Factory buildFactory();
  }

  /** Ensures the existence of a template, creating it if it does not exist. */
  protected abstract void ensureTemplate(String name, String indexTemplate);

  /** Deletes the specified index pattern is supplied */
  protected abstract void clear(String index);

  /**
   * Scans the given indices with the given query and aggregates. If aggregating all traces,
   * pass {@link QueryBuilders#matchAllQuery()}
   *
   * NB: This call is _lenient_ in its index selection, and only will match open indexes, cf.
   * {@link IndicesOptions#lenientExpandOpen()} }.
   *
   * Will never return results; this interface is for aggregation only.
   */
  protected abstract ListenableFuture<Buckets> scanTraces(String[] indices, QueryBuilder query,
      AbstractAggregationBuilder... aggregations);

  protected interface Buckets {
    /**
     * Retrieves the aggregation bucket keys from the aggregation at name[>nestedPath]
     *
     * The elasticsearch store only uses this one attribute of any aggregations, and so this
     * interface wraps the ugliness of dealing with different aggregation APIs while leaving control
     * of the aggregation keys in the hands of the caller.
     */
    // TODO(adrian): revisit once the other implementation is in: can we avoid varags?
    List<String> getBucketKeys(String name, String... nestedPath);
  }

  /**
   * Retrieves spans from the given indices. Pass {@link QueryBuilders#matchAllQuery()} for all
   * spans.
   *
   * NB: This call is _lenient_ in its index selection, and only will match open indexes, cf.
   * {@link IndicesOptions#lenientExpandOpen()}.
   */
  protected abstract ListenableFuture<List<Span>> findSpans(String[] indices, QueryBuilder query);

  /**
   * Retrieves all dependency links in the given indices. Useful for gathering dependencies from
   * a (time-bounded) set of "recent" indices.
   *
   * NB: This call is _lenient_ in its index selection, and only will match open indexes, cf.
   * {@link IndicesOptions#lenientExpandOpen()} }.
   */
  // TODO(adrian): revisit once the other implementation is in: why we are using arrays vs lists?
  protected abstract ListenableFuture<Collection<DependencyLink>> findDependencies(String[] indices);

  /**
   * Indexes the given spans, flushing depending on the value of
   * {@link Builder#flushOnWrites(boolean)}
   */
  protected abstract ListenableFuture<Void> indexSpans(List<IndexableSpan> spans);

  protected static final class IndexableSpan {
    public final String index;
    public final byte[] data;

    IndexableSpan(String index, byte[] data) {
      this.index = index;
      this.data = data;
    }
  }

  /**
   * Throws an exception if the health status is red, or a connection couldn't be made.
   *
   * @param catchAll See {@link IndexNameFormatter#catchAll()}
   */
  protected abstract void ensureClusterReady(String catchAll);

  /** Overridden to remove checked exceptions. */
  @Override
  public abstract void close();
}
