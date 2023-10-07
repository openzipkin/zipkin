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

package zipkin.server.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.google.common.base.Joiner;
import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class CassandraTableInstaller extends JDBCTableInstaller {
  public CassandraTableInstaller(Client client, ModuleManager moduleManager) {
    super(client, moduleManager);
  }

  @Override
  public boolean isExists(Model model) {
    TableMetaInfo.addModel(model);

    final String table = TableHelper.getLatestTableForWrite(model);

    final Optional<TableMetadata> tableMetadata = ((CassandraClient) client).getMetadata().getTable(table);
    if (!tableMetadata.isPresent()) {
      return false;
    }

    final Set<String> databaseColumns = getDatabaseColumns(table);
    final boolean isAnyColumnNotCreated =
        model
            .getColumns().stream()
            .map(ModelColumn::getColumnName)
            .map(ColumnName::getStorageName)
            .anyMatch(c -> !databaseColumns.contains(c));

    return !isAnyColumnNotCreated;
  }

  public void createTable(Model model, long timeBucket) {
    try {
      final String table = TableHelper.getTable(model, timeBucket);
      createOrUpdateTable(model, table, model.getColumns(), false);
      createOrUpdateTableIndexes(model, table, model.getColumns(), false);
      createAdditionalTable(model, timeBucket);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public void createAdditionalTable(Model model, long timeBucket) throws SQLException {
    final Map<String, SQLDatabaseModelExtension.AdditionalTable> additionalTables = model.getSqlDBModelExtension().getAdditionalTables();
    for (final SQLDatabaseModelExtension.AdditionalTable table : additionalTables.values()) {
      final String tableName = TableHelper.getTable(table.getName(), timeBucket);
      createOrUpdateTable(model, tableName, table.getColumns(), true);
      createOrUpdateTableIndexes(model, tableName, table.getColumns(), true);
    }
  }

  public void createOrUpdateTable(Model model, String table, List<ModelColumn> columns, boolean isAdditionalTable) {
    try {
      final List<ModelColumn> columnsToBeAdded = new ArrayList<>(columns);
      final Set<String> existingColumns = getDatabaseColumns(table);

      columnsToBeAdded.removeIf(it -> existingColumns.contains(it.getColumnName().getStorageName()));

      final KeyspaceMetadata metadata = ((CassandraClient) this.client).getMetadata();
      if (!metadata.getTable(table).isPresent()) {
        createTable(model, table, columnsToBeAdded, isAdditionalTable);
      } else {
        updateTable(table, columnsToBeAdded);
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public void createOrUpdateTableIndexes(Model model, String table, List<ModelColumn> columns, boolean isAdditionalTable) throws SQLException {
    final CassandraClient cassandraClient = (CassandraClient) this.client;

    final List<String> columnsMissingIndex =
        columns
            .stream()
            .filter(ModelColumn::shouldIndex)
            .filter(it -> it.getLength() < 256)
            .filter(c -> !model.isRecord() || c.getBanyanDBExtension().getShardingKeyIdx() > 0)
            .map(ModelColumn::getColumnName)
            .map(ColumnName::getStorageName)
            .collect(toList());

    // adding the time_bucket as an index column when querying zipkin_query
    columnsMissingIndex.add("time_bucket");
    for (String column : columnsMissingIndex) {
      final String index = "idx_" + table + "_" + column;
      if (!indexExists(cassandraClient, table, index)) {
        executeSQL(
            new SQLBuilder("CREATE INDEX ")
                .append(index)
                .append(" ON ").append(table).append("(")
                .append(column)
                .append(")")
        );
      }
    }
  }

  private boolean indexExists(CassandraClient client, String tableName, String indexName) {
    final TableMetadata tableMetadata = client.getMetadata().getTable(tableName).orElse(null);
    if (tableMetadata == null) {
      return false;
    }
    return tableMetadata.getIndex(indexName).isPresent();
  }

  private void createTable(Model model, String table, List<ModelColumn> columns, boolean isAdditionalTable) throws SQLException {
    final List<String> columnDefinitions = new ArrayList<>();
    columnDefinitions.add(ID_COLUMN + " text");
    if (!isAdditionalTable) {
      columnDefinitions.add(JDBCTableInstaller.TABLE_COLUMN + " text");
    }

    columns
        .stream()
        .map(this::getColumnDefinition)
        .forEach(columnDefinitions::add);

    List<String> shardKeys = columns.stream()
        .filter(column -> column.getBanyanDBExtension() != null && column.getBanyanDBExtension().getShardingKeyIdx() >= 0)
        .map(t -> t.getColumnName().getStorageName())
        .collect(Collectors.toList());

    // if existing time bucket field, then add it to the primary key for filtering
    if (columns.stream().anyMatch(s -> s.getColumnName().getStorageName().equals(StorageData.TIME_BUCKET))) {
      shardKeys.add(StorageData.TIME_BUCKET);
    }

    // make sure all the record can be inserted(ignore primary check)
    if (model.isRecord() && !isAdditionalTable) {
      columnDefinitions.add(CassandraClient.RECORD_UNIQUE_UUID_COLUMN + " text");
      shardKeys.add(CassandraClient.RECORD_UNIQUE_UUID_COLUMN);
    }

    // record don't need to add the ID Column as partition key(for query performance)
    if (model.isRecord()) {
      columnDefinitions.add("PRIMARY KEY (" + (CollectionUtils.isEmpty(shardKeys) ? ID_COLUMN : Joiner.on(", ").join(shardKeys)) + ")");
    } else {
      columnDefinitions.add("PRIMARY KEY (" + ID_COLUMN + (CollectionUtils.isEmpty(shardKeys) ? "" : "," + Joiner.on(", ").join(shardKeys)) + ")");
    }

    final SQLBuilder sql = new SQLBuilder("CREATE TABLE IF NOT EXISTS " + table)
        .append(columnDefinitions.stream().collect(joining(", ", " (", ")")))
        .append(" WITH ");

    if (CollectionUtils.isNotEmpty(shardKeys)) {
      if (model.isRecord()) shardKeys.remove(0);
      sql.append(" CLUSTERING ORDER BY (")
          .append(shardKeys.stream().map(s -> s + " DESC").collect(joining(", ")))
          .append(") AND ");
    }

    sql
        .append("   compaction = {'class': 'org.apache.cassandra.db.compaction.TimeWindowCompactionStrategy'} ")
        .append("   AND gc_grace_seconds = 3600")
        .append("   AND speculative_retry = '95percentile';");

    executeSQL(sql);
  }

  private void updateTable(String table, List<ModelColumn> columns) throws SQLException {
    final List<String> alterSqls = columns
        .stream()
        .map(this::getColumnDefinition)
        .map(definition -> "ALTER TABLE " + table + " ADD COLUMN " + definition + "; ")
        .collect(toList());

    for (String alterSql : alterSqls) {
      executeSQL(new SQLBuilder(alterSql));
    }
  }

  @Override
  public void executeSQL(SQLBuilder sql) throws SQLException {
    ((CassandraClient) this.client).execute(sql.toString());
  }

  @Override
  protected String getColumnDefinition(ModelColumn column, Class<?> type, Type genericType) {
    final String storageName = column.getColumnName().getStorageName();
    if (Integer.class.equals(type) || int.class.equals(type) || Layer.class.equals(type)) {
      return storageName + " int";
    } else if (Long.class.equals(type) || long.class.equals(type)) {
      return storageName + " bigint";
    } else if (Double.class.equals(type) || double.class.equals(type)) {
      return storageName + " DOUBLE";
    } else if (String.class.equals(type)) {
      return storageName + " text";
    } else if (StorageDataComplexObject.class.isAssignableFrom(type)) {
      return storageName + " text";
    } else if (byte[].class.equals(type)) {
      return storageName + " blob";
    } else if (JsonObject.class.equals(type)) {
      return storageName + " text";
    } else if (List.class.isAssignableFrom(type)) {
      final Type elementType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
      return getColumnDefinition(column, (Class<?>) elementType, elementType);
    } else {
      throw new IllegalArgumentException("Unsupported data type: " + type.getName());
    }
  }

  protected Set<String> getDatabaseColumns(String table) {
    final KeyspaceMetadata metadata = ((CassandraClient) this.client).getMetadata();
    if (metadata == null) {
      return Collections.emptySet();
    }
    final TableMetadata tableMetadata = metadata.getTable(table).orElse(null);
    if (tableMetadata == null) {
      return Collections.emptySet();
    }
    return tableMetadata.getColumns().keySet().stream().map(CqlIdentifier::asInternal).collect(Collectors.toSet());
  }

}
