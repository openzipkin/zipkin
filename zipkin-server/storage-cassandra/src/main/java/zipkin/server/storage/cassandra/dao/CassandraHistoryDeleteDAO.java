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

package zipkin.server.storage.cassandra.dao;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableInstaller;

import java.io.IOException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CassandraHistoryDeleteDAO implements IHistoryDeleteDAO {
  static final Logger LOG = LoggerFactory.getLogger(CassandraHistoryDeleteDAO.class);

  private final CassandraClient client;
  private final TableHelper tableHelper;
  private final CassandraTableInstaller modelInstaller;
  private final Clock clock;

  private final Map<String, Long> lastDeletedTimeBucket = new ConcurrentHashMap<>();

  public CassandraHistoryDeleteDAO(CassandraClient client, TableHelper tableHelper, CassandraTableInstaller modelInstaller, Clock clock) {
    this.client = client;
    this.tableHelper = tableHelper;
    this.modelInstaller = modelInstaller;
    this.clock = clock;
  }

  @Override
  public void deleteHistory(Model model, String timeBucketColumnName, int ttl) throws IOException {
    final long endTimeBucket = TimeBucket.getTimeBucket(clock.millis() + TimeUnit.DAYS.toMillis(1), DownSampling.Day);
    final long startTimeBucket = TimeBucket.getTimeBucket(clock.millis() - TimeUnit.DAYS.toMillis(ttl), DownSampling.Day);
    LOG.info(
        "Deleting history data, ttl: {}, now: {}. Keep [{}, {}]",
        ttl,
        clock.millis(),
        startTimeBucket,
        endTimeBucket
    );

    final long deadline = Long.parseLong(new DateTime().minusDays(ttl).toString("yyyyMMdd"));
    final long lastSuccessDeadline = lastDeletedTimeBucket.getOrDefault(model.getName(), 0L);
    if (deadline <= lastSuccessDeadline) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(
            "The deadline {} is less than the last success deadline {}, skip deleting history data",
            deadline,
            lastSuccessDeadline
        );
      }
      return;
    }

    final List<String> ttlTables = tableHelper.getTablesInTimeBucketRange(model.getName(), startTimeBucket, endTimeBucket);
    final HashSet<String> tablesToDrop = new HashSet<String>();
    final String tableName = TableHelper.getTableName(model);

    client.getMetadata().getTables().keySet().stream()
        .map(CqlIdentifier::asInternal)
        .filter(s -> s.startsWith(tableName))
        .forEach(tablesToDrop::add);

    ttlTables.forEach(tablesToDrop::remove);
    tablesToDrop.removeIf(it -> !it.matches(tableName + "_\\d{8}$"));
    for (final String table : tablesToDrop) {
      final SQLBuilder dropSql = new SQLBuilder("drop table if exists ").append(table);
      client.execute(dropSql.toString());
    }

    // Drop additional tables
    for (final String table : tablesToDrop) {
      final long timeBucket = TableHelper.getTimeBucket(table);
      for (final SQLDatabaseModelExtension.AdditionalTable additionalTable : model.getSqlDBModelExtension().getAdditionalTables().values()) {
        final String additionalTableToDrop = TableHelper.getTable(additionalTable.getName(), timeBucket);
        final SQLBuilder dropSql = new SQLBuilder("drop table if exists ").append(additionalTableToDrop);
        client.execute(dropSql.toString());
      }
    }

    // Create tables for the next day.
    final long nextTimeBucket = TimeBucket.getTimeBucket(clock.millis() + TimeUnit.DAYS.toMillis(1), DownSampling.Day);
    modelInstaller.createTable(model, nextTimeBucket);

    lastDeletedTimeBucket.put(model.getName(), deadline);
  }
}
