/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.storage.cassandra;

import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.List;
import org.junit.rules.TestName;
import zipkin2.DependencyLink;

import static org.assertj.core.api.Assertions.assertThat;

class InternalForTests {
  static void writeDependencyLinks(
    CassandraStorage storage, List<DependencyLink> links, long midnightUTC) {
    for (DependencyLink link : links) {
      Insert statement =
        QueryBuilder.insertInto(Schema.TABLE_DEPENDENCY)
          .value("day", LocalDate.fromMillisSinceEpoch(midnightUTC))
          .value("parent", link.parent())
          .value("child", link.child())
          .value("calls", link.callCount())
          .value("errors", link.errorCount());
      storage.session().execute(statement);
    }
  }

  static String keyspace(TestName testName) {
    String result = testName.getMethodName().toLowerCase();
    return result.length() <= 48 ? result : result.substring(result.length() - 48);
  }

  static void dropKeyspace(Session session, String keyspace) {
    session.execute("DROP KEYSPACE IF EXISTS " + keyspace);
    assertThat(session.getCluster().getMetadata().getKeyspace(keyspace)).isNull();
  }
}
