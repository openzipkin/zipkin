/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.storage.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import static org.jooq.impl.DSL.count;
import static zipkin.storage.mysql.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;

/**
 * Returns true when the zipkin_dependencies table exists and has data in it, implying the spark job
 * has been run.
 */
final class HasPreAggregatedDependencies {
  private static final Logger LOG = Logger.getLogger(HasPreAggregatedDependencies.class.getName());

  static boolean test(DataSource datasource, DSLContexts context) {
    try (Connection conn = datasource.getConnection()) {
      DSLContext dsl = context.get(conn);
      return dsl.select(count()).from(ZIPKIN_DEPENDENCIES).fetchAny().value1() > 0;
    } catch (DataAccessException e) {
      if (e.sqlState().equals("42S02")) {
        LOG.warning("zipkin_dependencies doesn't exist, so pre-aggregated dependencies are not " +
            "supported. Execute mysql.sql located in this jar to add the table");
        return false;
      }
      problemReading(e);
    } catch (SQLException | RuntimeException e) {
      problemReading(e);
    }
    return false;
  }

  static void problemReading(Exception e) {
    LOG.log(Level.WARNING, "problem reading zipkin_dependencies", e);
  }
}
