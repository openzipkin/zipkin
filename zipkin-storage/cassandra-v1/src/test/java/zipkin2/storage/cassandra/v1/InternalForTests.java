/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.TestInfo;
import zipkin2.DependencyLink;
import zipkin2.internal.Dependencies;

class InternalForTests {

  static void writeDependencyLinks(
    CassandraStorage storage, List<DependencyLink> links, long midnightUTC) {
    Dependencies deps = Dependencies.create(midnightUTC, midnightUTC /* ignored */, links);
    ByteBuffer thrift = deps.toThrift();
    Insert statement =
      QueryBuilder.insertInto("dependencies")
        .value("day", new Date(midnightUTC))
        .value("dependencies", thrift);
    storage.session().execute(statement);
  }

  static String keyspace(TestInfo testInfo) {
    String result;
    if (testInfo.getTestMethod().isPresent()) {
      result = testInfo.getTestMethod().get().getName();
    } else {
      assert testInfo.getTestClass().isPresent();
      result = testInfo.getTestClass().get().getSimpleName();
    }
    result = result.toLowerCase();
    return result.length() <= 48 ? result : result.substring(result.length() - 48);
  }
}
