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

import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinSpans.ZIPKIN_SPANS;

final class HasRemoteServiceName {
  static final Logger LOG = Logger.getLogger(HasRemoteServiceName.class.getName());
  static final String MESSAGE =
    """
    zipkin_spans.remote_service_name doesn't exist, so queries for remote service names will return empty.
    Execute: ALTER TABLE zipkin_spans ADD `remote_service_name` VARCHAR(255);
    ALTER TABLE zipkin_spans ADD INDEX `remote_service_name`;\
    """;

  static boolean test(DataSource datasource, DSLContexts context) {
    try (Connection conn = datasource.getConnection()) {
      DSLContext dsl = context.get(conn);
      dsl.select(ZIPKIN_SPANS.REMOTE_SERVICE_NAME).from(ZIPKIN_SPANS).limit(1).fetchAny();
      return true;
    } catch (DataAccessException e) {
      if (e.sqlState().equals("42S22")) {
        LOG.warning(MESSAGE);
        return false;
      }
      problemReading(e);
    } catch (SQLException | RuntimeException e) {
      problemReading(e);
    }
    return false;
  }

  static void problemReading(Exception e) {
    LOG.log(Level.WARNING, "problem reading zipkin_spans.remote_service_name", e);
  }
}
