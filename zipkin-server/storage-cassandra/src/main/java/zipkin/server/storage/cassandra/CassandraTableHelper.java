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

import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static java.util.stream.Collectors.toList;

public class CassandraTableHelper extends TableHelper {
  private ModuleManager moduleManager;
  private final CassandraClient client;

  private final LoadingCache<String, Boolean> tableExistence =
      CacheBuilder.newBuilder()
          .expireAfterWrite(Duration.ofMinutes(10))
          .build(new CacheLoader<String, Boolean>() {
            @Override
            public Boolean load(String tableName) throws Exception {
              final KeyspaceMetadata metadata = client.getMetadata();
              return metadata != null && metadata.getTable(tableName).isPresent();
            }
          });

  public CassandraTableHelper(ModuleManager moduleManager, CassandraClient client) {
    super(moduleManager, null);
    this.moduleManager = moduleManager;
    this.client = client;
  }

  public List<String> getTablesForRead(String modelName, long timeBucketStart, long timeBucketEnd) {
    final Model model = TableMetaInfo.get(modelName);
    final String rawTableName = getTableName(model);

    if (!model.isTimeSeries()) {
      return Collections.singletonList(rawTableName);
    }

    final List<String> ttlTables = getTablesWithinTTL(modelName);
    return getTablesInTimeBucketRange(modelName, timeBucketStart, timeBucketEnd)
        .stream()
        .filter(ttlTables::contains)
        .filter(table -> {
          try {
            return tableExistence.get(table);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        })
        .collect(toList());
  }

  public List<String> getTablesWithinTTL(String modelName) {
    final Model model = TableMetaInfo.get(modelName);
    final String rawTableName = getTableName(model);

    if (!model.isTimeSeries()) {
      return Collections.singletonList(rawTableName);
    }

    final List<Long> ttlTimeBuckets = getTTLTimeBuckets(model);
    return ttlTimeBuckets
        .stream()
        .map(it -> getTable(rawTableName, it))
        .filter(table -> {
          try {
            return tableExistence.get(table);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        })
        .collect(toList());
  }

  List<Long> getTTLTimeBuckets(Model model) {
    final int ttl = model.isRecord() ?
        getConfigService().getRecordDataTTL() :
        getConfigService().getMetricsDataTTL();
    return LongStream
        .rangeClosed(0, ttl)
        .mapToObj(it -> TimeBucket.getTimeBucket(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it), DownSampling.Day))
        .distinct()
        .collect(toList());
  }

  ConfigService getConfigService() {
    return moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class);
  }
}
