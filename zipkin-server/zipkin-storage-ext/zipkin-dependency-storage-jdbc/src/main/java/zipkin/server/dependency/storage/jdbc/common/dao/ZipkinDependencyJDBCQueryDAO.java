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

package zipkin.server.dependency.storage.jdbc.common.dao;

import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.apache.skywalking.zipkin.dependency.entity.ZipkinDependency;
import zipkin.server.dependency.IZipkinDependencyQueryDAO;
import zipkin2.DependencyLink;
import zipkin2.internal.DependencyLinker;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static zipkin2.internal.DateUtil.epochDays;

public class ZipkinDependencyJDBCQueryDAO implements IZipkinDependencyQueryDAO {
  private JDBCClient client;
  private TableHelper tableHelper;

  @Override
  public List<DependencyLink> getDependencies(long endTs, long lookback) throws IOException {
    final List<Long> days = epochDays(endTs, lookback);

    final long startBucket = TimeBucket.getTimeBucket(endTs - lookback, DownSampling.Day);
    final long endBucket = TimeBucket.getTimeBucket(endTs, DownSampling.Day);
    final List<DependencyLink> result = new ArrayList<>();

    for (String table : tableHelper.getTablesForRead(ZipkinDependency.INDEX_NAME, startBucket, endBucket)) {
      try {
        client.executeQuery("select * from " + table + " where " + ZipkinDependency.DAY + " in "
                + days.stream().map(it -> "?").collect(joining(",", "(", ")")),
            resultSet -> {
              while (resultSet.next()) {
                result.add(DependencyLink.newBuilder()
                    .parent(resultSet.getString(ZipkinDependency.PARENT))
                    .child(resultSet.getString(ZipkinDependency.CHILD))
                    .callCount(resultSet.getLong(ZipkinDependency.CALL_COUNT))
                    .errorCount(resultSet.getLong(ZipkinDependency.ERROR_COUNT))
                    .build());
              }
              return null;
            }, days.toArray());
      } catch (SQLException e) {
        throw new IOException(e);
      }
    }
    return DependencyLinker.merge(result);
  }

  public void setClient(JDBCClient client) {
    this.client = client;
  }

  public void setTableHelper(TableHelper tableHelper) {
    this.tableHelper = tableHelper;
  }
}
