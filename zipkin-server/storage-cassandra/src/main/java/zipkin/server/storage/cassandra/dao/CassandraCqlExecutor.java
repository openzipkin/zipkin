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

import com.datastax.oss.driver.api.core.cql.Row;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import zipkin.server.storage.cassandra.CQLExecutor;
import zipkin.server.storage.cassandra.CassandraClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller.ID_COLUMN;

public class CassandraCqlExecutor {

  protected <T extends StorageData> List<StorageData> getByIDs(CassandraClient client,
                                                               String modelName,
                                                               List<String> ids,
                                                               StorageBuilder<T> storageBuilder) {
    List<StorageData> storageDataList = new ArrayList<>();

    final String cql = "SELECT * FROM " + getModelTables(client, modelName) + " WHERE id in " +
        ids.stream().map(it -> "?").collect(Collectors.joining(",", "(", ") ALLOW FILTERING"));
    storageDataList.addAll(client.executeQuery(cql, resultSet -> toStorageData(resultSet, modelName, storageBuilder), ids.toArray()));

    return storageDataList;
  }

  protected <T extends StorageData>CQLExecutor getInsertExecutor(Model model, T metrics, long timeBucket,
                                                                 StorageBuilder<T> storageBuilder,
                                                                 Convert2Storage<Map<String, Object>> converter,
                                                                 SessionCacheCallback callback) {
    storageBuilder.entity2Storage(metrics, converter);
    // adding the uuid column
    Map<String, Object> objectMap = converter.obtain();
    //build main table cql
    Map<String, Object> mainEntity = new HashMap<>();
    model.getColumns().forEach(column -> {
      mainEntity.put(column.getColumnName().getName(), objectMap.get(column.getColumnName().getName()));
    });
    CQLExecutor sqlExecutor = buildInsertExecutor(
        model, metrics, timeBucket, mainEntity, callback);
    //build additional table cql
    for (final SQLDatabaseModelExtension.AdditionalTable additionalTable : model.getSqlDBModelExtension().getAdditionalTables().values()) {
      Map<String, Object> additionalEntity = new HashMap<>();
      additionalTable.getColumns().forEach(column -> {
        additionalEntity.put(column.getColumnName().getName(), objectMap.get(column.getColumnName().getName()));
      });

      List<CQLExecutor> additionalSQLExecutors = buildAdditionalInsertExecutor(
          model, additionalTable.getName(), additionalTable.getColumns(), metrics,
          timeBucket, additionalEntity, callback
      );
      sqlExecutor.appendAdditionalCQLs(additionalSQLExecutors);
    }

    // extension the span tables for query
    if (metrics instanceof ZipkinSpanRecord) {
      sqlExecutor.appendAdditionalCQLs(CassandraTableExtension.buildExtensionsForSpan((ZipkinSpanRecord) metrics, callback));
    }
    return sqlExecutor;
  }

  protected <T extends StorageData> CQLExecutor getUpdateExecutor(Model model, T metrics,
                                                                  long timeBucket,
                                                                  StorageBuilder<T> storageBuilder,
                                                                  SessionCacheCallback callback) {
    final Convert2Storage<Map<String, Object>> toStorage = new HashMapConverter.ToStorage();
    storageBuilder.entity2Storage(metrics, toStorage);
    final Map<String, Object> objectMap = toStorage.obtain();
    final String table = TableHelper.getTable(model, timeBucket);
    final StringBuilder sqlBuilder = new StringBuilder("UPDATE " + table + " SET ");
    final List<ModelColumn> columns = model.getColumns();
    final List<String> queries = new ArrayList<>();
    final List<Object> param = new ArrayList<>();
    for (final ModelColumn column : columns) {
      final String columnName = column.getColumnName().getName();
      queries.add(column.getColumnName().getStorageName() + " = ?");

      final Object value = objectMap.get(columnName);
      if (value instanceof StorageDataComplexObject) {
        param.add(((StorageDataComplexObject) value).toStorageData());
      } else {
        param.add(value);
      }
    }
    sqlBuilder.append(queries.stream().collect(Collectors.joining(", ")));
    sqlBuilder.append(" WHERE id = ?");
    param.add(TableHelper.generateId(model, metrics.id().build()));

    return new CQLExecutor(sqlBuilder.toString(), param, callback, null);
  }

  private <T extends StorageData> List<CQLExecutor> buildAdditionalInsertExecutor(Model model, String tableName,
                                                                                  List<ModelColumn> columns,
                                                                                  T metrics,
                                                                                  long timeBucket,
                                                                                  Map<String, Object> objectMap,
                                                                                  SessionCacheCallback callback) {

    List<CQLExecutor> sqlExecutors = new ArrayList<>();
    List<String> columnNames = new ArrayList<>();
    List<String> values = new ArrayList<>();
    List<Object> param = new ArrayList<>();
    final StringBuilder cqlBuilder = new StringBuilder("INSERT INTO ").append(tableName);

    int position = 0;
    List valueList = new ArrayList();
    for (int i = 0; i < columns.size(); i++) {
      ModelColumn column = columns.get(i);
      if (List.class.isAssignableFrom(column.getType())) {
        valueList = (List) objectMap.get(column.getColumnName().getName());

        columnNames.add(column.getColumnName().getStorageName());
        values.add("?");
        param.add(null);

        position = i + 1;
      } else {
        columnNames.add(column.getColumnName().getStorageName());
        values.add("?");

        Object value = objectMap.get(column.getColumnName().getName());
        if (value instanceof StorageDataComplexObject) {
          param.add(((StorageDataComplexObject) value).toStorageData());
        } else {
          param.add(value);
        }
      }
    }

    cqlBuilder.append("(").append(columnNames.stream().collect(Collectors.joining(", "))).append(")")
        .append(" VALUES (").append(values.stream().collect(Collectors.joining(", "))).append(")");
    String sql = cqlBuilder.toString();
    if (!CollectionUtils.isEmpty(valueList)) {
      for (Object object : valueList) {
        List<Object> paramCopy = new ArrayList<>(param);
        paramCopy.set(position - 1, object);
        sqlExecutors.add(new CQLExecutor(sql, paramCopy, callback, null));
      }
    } else {
      // if not query data, then ignore the data insert
      if ("zipkin_query".equals(tableName)) {
        return sqlExecutors;
      }
      sqlExecutors.add(new CQLExecutor(sql, param, callback, null));
    }

    return sqlExecutors;
  }

  private <T extends StorageData> CQLExecutor buildInsertExecutor(Model model,
                                                                  T metrics,
                                                                  long timeBucket,
                                                                  Map<String, Object> objectMap,
                                                                  SessionCacheCallback onCompleteCallback) {
    final String table = TableHelper.getTableName(model);
    final StringBuilder cqlBuilder = new StringBuilder("INSERT INTO ").append(table);
    final List<ModelColumn> columns = model.getColumns();
    final List<String> columnNames =
        Stream.concat(
                (model.isRecord() ? Collections.<String>emptyList() : Arrays.asList(ID_COLUMN, JDBCTableInstaller.TABLE_COLUMN)).stream(),
                columns
                    .stream()
                    .map(ModelColumn::getColumnName)
                    .map(ColumnName::getStorageName))
            .collect(Collectors.toList());
    if (model.isRecord()) {
      columnNames.add(CassandraClient.RECORD_UNIQUE_UUID_COLUMN);
    }
    cqlBuilder.append(columnNames.stream().collect(Collectors.joining(",", "(", ")")));
    cqlBuilder.append(" VALUES ");
    cqlBuilder.append(columnNames.stream().map(it -> "?").collect(Collectors.joining(",", "(", ")")));

    final List<Object> params = Stream.concat(
            (model.isRecord() ? Collections.<String>emptyList() : Arrays.asList(TableHelper.generateId(model, metrics.id().build()), model.getName())).stream(),
            columns
                .stream()
                .map(ModelColumn::getColumnName)
                .map(ColumnName::getName)
                .map(objectMap::get)
                .map(it -> {
                  if (it instanceof StorageDataComplexObject) {
                    return ((StorageDataComplexObject) it).toStorageData();
                  }
                  return it;
                }))
        .collect(Collectors.toList());
    if (model.isRecord()) {
      params.add(UUID.randomUUID().toString());
    }

    return new CQLExecutor(cqlBuilder.toString(), params, onCompleteCallback, null);
  }


  protected StorageData toStorageData(Row row, String modelName,
                                      StorageBuilder<? extends StorageData> storageBuilder) {
    Map<String, Object> data = new HashMap<>();
    List<ModelColumn> columns = TableMetaInfo.get(modelName).getColumns();
    for (ModelColumn column : columns) {
      data.put(column.getColumnName().getName(), row.getObject(column.getColumnName().getStorageName()));
    }
    return storageBuilder.storage2Entity(new HashMapConverter.ToEntity(data));
  }

  private static String getModelTables(CassandraClient client, String modelName) {
    final Model model = TableMetaInfo.get(modelName);
    return TableHelper.getTableName(model);
  }
}
