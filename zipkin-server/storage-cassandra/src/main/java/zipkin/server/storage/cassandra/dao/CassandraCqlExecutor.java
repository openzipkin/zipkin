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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
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
import static zipkin2.internal.RecyclableBuffers.SHORT_STRING_LENGTH;

public class CassandraCqlExecutor {
  private static final JsonObject EMPTY_JSON_OBJECT = new JsonObject();

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
    if (metrics instanceof ZipkinSpanRecord) {
      columnNames.add(CassandraClient.RECORD_UNIQUE_UUID_COLUMN);
      columnNames.add(CassandraClient.ZIPKIN_SPAN_ANNOTATION_QUERY_COLUMN);
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
    if (metrics instanceof ZipkinSpanRecord) {
      params.add(UUID.randomUUID().toString());
      params.add(annotationQuery((ZipkinSpanRecord) metrics));
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

  private String annotationQuery(ZipkinSpanRecord span) {
    final JsonObject annotation = jsonCheck(span.getAnnotations());
    final JsonObject tags = jsonCheck(span.getTags());
    if (annotation.size() == 0 && tags.size() == 0) return null;

    char delimiter = '░'; // as very unlikely to be in the query
    StringBuilder result = new StringBuilder().append(delimiter);
    for (Map.Entry<String, JsonElement> annotationEntry : annotation.entrySet()) {
      final String annotationValue = annotationEntry.getValue().getAsString();
      if (annotationValue.length() > SHORT_STRING_LENGTH) continue;

      result.append(annotationValue).append(delimiter);
    }

    for (Map.Entry<String, JsonElement> tagEntry : tags.entrySet()) {
      final String tagValue = tagEntry.getValue().getAsString();
      if (tagValue.length() > SHORT_STRING_LENGTH) continue;

      result.append(tagEntry.getKey()).append(delimiter); // search is possible by key alone
      result.append(tagEntry.getKey()).append('=').append(tagValue).append(delimiter);
    }
    return result.length() == 1 ? null : result.toString();
  }

  private JsonObject jsonCheck(JsonObject json) {
    if (json == null) {
      json = EMPTY_JSON_OBJECT;
    }
    return json;
  }
}
