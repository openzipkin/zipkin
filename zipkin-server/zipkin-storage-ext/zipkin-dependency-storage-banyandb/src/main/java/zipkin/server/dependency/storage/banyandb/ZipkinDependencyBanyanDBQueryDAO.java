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

package zipkin.server.dependency.storage.banyandb;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.zipkin.dependency.entity.ZipkinDependency;
import zipkin.server.dependency.IZipkinDependencyQueryDAO;
import zipkin2.DependencyLink;
import zipkin2.internal.DependencyLinker;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static zipkin2.internal.DateUtil.epochDays;

public class ZipkinDependencyBanyanDBQueryDAO implements IZipkinDependencyQueryDAO {
  protected BanyanDBStorageClient client;
  final Set<String> tags = ImmutableSet.of(ZipkinDependency.DAY, ZipkinDependency.PARENT, ZipkinDependency.CHILD);
  final Set<String> fields = ImmutableSet.of(ZipkinDependency.CALL_COUNT, ZipkinDependency.ERROR_COUNT);

  @Override
  public List<DependencyLink> getDependencies(long endTs, long lookback) throws IOException {
    final List<Long> days = epochDays(endTs, lookback);
    final TimestampRange timeRange = new TimestampRange(days.get(0), days.get(days.size() - 1) + 1);
    MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ZipkinDependency.INDEX_NAME, Step.MINUTE);
    final MeasureQuery query = new MeasureQuery(schema.getMetadata().getGroup(), schema.getMetadata().name(), timeRange, tags, fields);
    final MeasureQueryResponse result = client.query(query);
    return DependencyLinker.merge(result.getDataPoints().stream().map(s -> DependencyLink.newBuilder()
        .parent(s.getTagValue(ZipkinDependency.PARENT))
        .child(s.getTagValue(ZipkinDependency.CHILD))
        .callCount(((Number)s.getFieldValue(ZipkinDependency.CALL_COUNT)).longValue())
        .errorCount(s.getFieldValue(ZipkinDependency.ERROR_COUNT) != null ? ((Number)s.getFieldValue(ZipkinDependency.ERROR_COUNT)).longValue() : 0)
        .build()).collect(Collectors.toList()));
  }

  public void setClient(BanyanDBStorageClient client) {
    this.client = client;
  }

}
