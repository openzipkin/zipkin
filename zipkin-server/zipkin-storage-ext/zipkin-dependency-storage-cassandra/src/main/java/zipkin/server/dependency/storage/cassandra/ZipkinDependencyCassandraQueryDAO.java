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

package zipkin.server.dependency.storage.cassandra;

import org.apache.skywalking.zipkin.dependency.entity.ZipkinDependency;
import zipkin.server.dependency.IZipkinDependencyQueryDAO;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin2.DependencyLink;
import zipkin2.internal.DateUtil;
import zipkin2.internal.DependencyLinker;
import zipkin2.internal.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class ZipkinDependencyCassandraQueryDAO implements IZipkinDependencyQueryDAO {
  private CassandraClient client;
  @Override
  public List<DependencyLink> getDependencies(long endTs, long lookback) throws IOException {
    return DependencyLinker.merge(client.executeQuery("SELECT parent,child,call_count,error_count from " + ZipkinDependency.INDEX_NAME +
        " where " + ZipkinDependency.DAY + " in ? ", r -> DependencyLink.newBuilder()
            .parent(r.getString(ZipkinDependency.PARENT))
            .child(r.getString(ZipkinDependency.CHILD))
            .callCount(r.getLong(ZipkinDependency.CALL_COUNT))
            .errorCount(r.getLong(ZipkinDependency.ERROR_COUNT)).build(), getDays(endTs, lookback)));
  }

  public void setClient(CassandraClient client) {
    this.client = client;
  }

  List<LocalDate> getDays(long endTs, @Nullable Long lookback) {
    List<LocalDate> result = new ArrayList<>();
    for (long epochMillis : DateUtil.epochDays(endTs, lookback)) {
      result.add(Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate());
    }
    return result;
  }

}
