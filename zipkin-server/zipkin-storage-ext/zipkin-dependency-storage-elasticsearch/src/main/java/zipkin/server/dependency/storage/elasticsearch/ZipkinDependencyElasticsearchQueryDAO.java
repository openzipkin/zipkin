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

package zipkin.server.dependency.storage.elasticsearch;

import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.zipkin.dependency.entity.ZipkinDependency;
import zipkin.server.dependency.IZipkinDependencyQueryDAO;
import zipkin2.DependencyLink;
import zipkin2.internal.DependencyLinker;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static zipkin2.internal.DateUtil.epochDays;

public class ZipkinDependencyElasticsearchQueryDAO implements IZipkinDependencyQueryDAO {
  private ElasticSearchClient client;
  @Override
  public List<DependencyLink> getDependencies(long endTs, long lookback) throws IOException {
    final List<Long> days = epochDays(endTs, lookback);

    final String index =
        IndexController.LogicIndicesRegister.getPhysicalTableName(ZipkinDependency.INDEX_NAME);
    final BoolQueryBuilder query = Query.bool();
    if (IndexController.LogicIndicesRegister.isMergedTable(ZipkinDependency.INDEX_NAME)) {
      query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, ZipkinDependency.INDEX_NAME));
    }
    query.must(Query.terms(ZipkinDependency.DAY, days));
    final SearchBuilder search = Search.builder().query(query)
        .size(days.size());

    final SearchResponse response = client.search(index, search.build());
    return DependencyLinker.merge(response.getHits().getHits().stream().map(h -> DependencyLink.newBuilder()
        .parent((String) h.getSource().get(ZipkinDependency.PARENT))
        .child((String) h.getSource().get(ZipkinDependency.CHILD))
        .callCount(((Number) h.getSource().get(ZipkinDependency.CALL_COUNT)).longValue())
        .errorCount(((Number) h.getSource().get(ZipkinDependency.ERROR_COUNT)).longValue())
        .build()).collect(Collectors.toList()));
  }

  public void setClient(ElasticSearchClient client) {
    this.client = client;
  }
}
