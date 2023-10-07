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

import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCTagAutoCompleteQueryDAO;
import zipkin.server.storage.cassandra.CassandraClient;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CassandraTagAutocompleteDAO extends JDBCTagAutoCompleteQueryDAO {
  private final CassandraClient client;
  private final TableHelper tableHelper;

  public CassandraTagAutocompleteDAO(CassandraClient client, TableHelper tableHelper) {
    super(null, tableHelper);
    this.client = client;
    this.tableHelper = tableHelper;
  }

  @Override
  public Set<String> queryTagAutocompleteKeys(TagType tagType, int limit, Duration duration) {
    final Set<String> results = new HashSet<>();

    for (String table : tableHelper.getTablesForRead(
        TagAutocompleteData.INDEX_NAME,
        duration.getStartTimeBucket(),
        duration.getEndTimeBucket()
    )) {
      final SQLAndParameters sqlAndParameters = buildSQLForQueryKeys(tagType, Integer.MAX_VALUE, duration, table);
      results.addAll(client.executeQuery(sqlAndParameters.sql().replaceAll("(1=1\\s+and)|(distinct)", "") + " ALLOW FILTERING",
          row -> row.getString(TagAutocompleteData.TAG_KEY), sqlAndParameters.parameters()));
    }
    return results.stream().distinct().limit(limit).collect(Collectors.toSet());
  }

  @Override
  public Set<String> queryTagAutocompleteValues(TagType tagType, String tagKey, int limit, Duration duration) {
    final Set<String> results = new HashSet<>();

    for (String table : tableHelper.getTablesForRead(
        TagAutocompleteData.INDEX_NAME,
        duration.getStartTimeBucket(),
        duration.getEndTimeBucket()
    )) {
      final SQLAndParameters sqlAndParameters = buildSQLForQueryValues(tagType, tagKey, limit, duration, table);
      results.addAll(client.executeQuery(sqlAndParameters.sql() + " ALLOW FILTERING",
          row -> row.getString(TagAutocompleteData.TAG_VALUE), sqlAndParameters.parameters()));
    }
    return results;
  }
}
