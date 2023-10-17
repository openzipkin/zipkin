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

import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCTagAutoCompleteQueryDAO;
import zipkin.server.core.services.ZipkinConfigService;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;

public class CassandraTagAutocompleteDAO extends JDBCTagAutoCompleteQueryDAO {
  private final CassandraClient client;
  private final CassandraTableHelper tableHelper;
  private final ModuleManager moduleManager;
  private Set<String> tagAutocompleteKeys;

  public CassandraTagAutocompleteDAO(CassandraClient client, CassandraTableHelper tableHelper, ModuleManager moduleManager) {
    super(null, tableHelper);
    this.client = client;
    this.tableHelper = tableHelper;
    this.moduleManager = moduleManager;
    this.tagAutocompleteKeys = null;
  }

  private Set<String> getTagAutocompleteKeys() {
    if (tagAutocompleteKeys != null) {
      return tagAutocompleteKeys;
    }
    final ConfigService service = moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class);
    tagAutocompleteKeys = Stream.of((((ZipkinConfigService) service).toZipkinReceiverConfig().getSearchableTracesTags())
        .split(Const.COMMA)).collect(Collectors.toSet());
    return tagAutocompleteKeys;
  }

  @Override
  public Set<String> queryTagAutocompleteKeys(TagType tagType, int limit, Duration duration) {
    return getTagAutocompleteKeys().stream().limit(limit).collect(Collectors.toSet());
  }

  @Override
  public Set<String> queryTagAutocompleteValues(TagType tagType, String tagKey, int limit, Duration duration) {
    String cql = "select " + TagAutocompleteData.TAG_VALUE + " from " + tableHelper.getTableForRead(TagAutocompleteData.INDEX_NAME)
        + " where " + TagAutocompleteData.TAG_KEY + " = ? and "
        + TagAutocompleteData.TIME_BUCKET + " >= ? and " + TagAutocompleteData.TIME_BUCKET + " <= ? limit ?";

    long startSecondTB = 0;
    long endSecondTB = 0;
    if (nonNull(duration)) {
      startSecondTB = duration.getStartTimeBucketInSec();
      endSecondTB = duration.getEndTimeBucketInSec();
    }

    long startTB = startSecondTB / 1000000 * 10000;
    long endTB = endSecondTB / 1000000 * 10000 + 2359;

    return new HashSet<>(client.executeQuery(cql,
        row -> row.getString(TagAutocompleteData.TAG_VALUE), tagKey, startTB, endTB, limit));
  }
}
