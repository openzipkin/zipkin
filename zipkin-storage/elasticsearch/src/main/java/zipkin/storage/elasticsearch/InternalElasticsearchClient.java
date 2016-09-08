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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import zipkin.DependencyLink;
import zipkin.Span;

/**
 * Common interface for different protocol implementations communicating with Elasticsearch.
 *
 * Due to the difficulties of shoehorning auth over the native Elasticsearch protocol, many PaaS
 * providers (like AWS) only offer REST interfaces to ElasticSearch (presumably they meet their
 * security requirements with an HTTP proxy layer).
 *
 * That means, in order to support both aaS elasticsearch and "native" elasticsearch, we require a
 * shim.
 */
interface InternalElasticsearchClient extends AutoCloseable {

  /**
   * Ensures the existence of a template, creating it if it does not exist.
   */
  void ensureTemplate(String name, String indexTemplate);

  /**
   * Deletes the specified index (or indices, if a pattern is supplied)
   */
  void clear(String index);

  /**
   * Scans the given indices with the given query and aggregates. If aggregating all traces,
   * pass {@link QueryBuilders#matchAllQuery()}
   *
   * NB: This call is _lenient_ in its index selection, and only will match open indexes, cf.
   * {@link IndicesOptions#lenientExpandOpen()} }.
   *
   * Will never return results; this interface is for aggregation only.
   */
  ListenableFuture<Buckets> scanTraces(String[] indices, QueryBuilder query,
      AbstractAggregationBuilder... aggregations);

  interface Buckets {
    /**
     * Retrieves the aggregation bucket keys from the aggregation at name[>nestedPath]
     *
     * The elasticsearch store only uses this one attribute of any aggregations, and so this
     * interface wraps the ugliness of dealing with different aggregation APIs while leaving control
     * of the aggregation keys in the hands of the caller.
     */
    List<String> getBucketKeys(String name, String... nestedPath);
  }

  /**
   * Retrieves spans from the given indices. Pass {@link QueryBuilders#matchAllQuery()} for all
   * spans.
   *
   * NB: This call is _lenient_ in its index selection, and only will match open indexes, cf.
   * {@link IndicesOptions#lenientExpandOpen()}.
   */
  ListenableFuture<List<Span>> findSpans(String[] indices, QueryBuilder query);

  /**
   * Retrieves all dependency links in the given indices. Useful for gathering dependencies from
   * a (time-bounded) set of "recent" indices.
   *
   * NB: This call is _lenient_ in its index selection, and only will match open indexes, cf.
   * {@link IndicesOptions#lenientExpandOpen()} }.
   */
  ListenableFuture<Collection<DependencyLink>> findDependencies(String[] indices);

  /**
   * Indexes the given dependency links, flushing unconditionally.
   */
  void indexDependencies(String index, List<IndexableLink> links);

  final class IndexableLink {
    final String id;
    final Map<String, ?> data;

    public IndexableLink(String id, Map<String, ?> data) {
      this.id = id;
      this.data = data;
    }
  }

  /**
   * Indexes the given spans, flushing depending on the value of
   * {@link ElasticsearchStorage#FLUSH_ON_WRITES}
   */
  ListenableFuture<Void> indexSpans(List<IndexableSpan> spans);

  final class IndexableSpan {
    final String index;
    final byte[] data;

    IndexableSpan(String index, byte[] data) {
      this.index = index;
      this.data = data;
    }
  }

  /**
   * Cluster health status
   */
  HealthStatus clusterHealth(String... indices);

  @SuppressWarnings("unused") enum HealthStatus {
    GREEN, YELLOW, RED
  }

  /**
   * See {@link AutoCloseable#close()}
   *
   * Overridden to remove checked exceptions.
   */
  @Override
  void close();

  interface ClientFactory {
    InternalElasticsearchClient create(String[] allIndices);
  }
}
