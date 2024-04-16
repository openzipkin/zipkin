/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import static org.jooq.impl.DSL.count;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;

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
        LOG.warning(
            """
            zipkin_dependencies doesn't exist, so pre-aggregated dependencies are not \
            supported. Execute mysql.sql located in this jar to add the table\
            """);
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
