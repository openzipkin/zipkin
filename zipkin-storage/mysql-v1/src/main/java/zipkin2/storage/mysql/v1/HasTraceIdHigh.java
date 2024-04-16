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

final class HasTraceIdHigh {
  static final Logger LOG = Logger.getLogger(HasTraceIdHigh.class.getName());
  static final String MESSAGE =
      """
      zipkin_spans.trace_id_high doesn't exist, so 128-bit trace ids are not supported. \
      Execute: ALTER TABLE zipkin_spans ADD `trace_id_high` BIGINT NOT NULL DEFAULT 0;
      ALTER TABLE zipkin_annotations ADD `trace_id_high` BIGINT NOT NULL DEFAULT 0;
      ALTER TABLE zipkin_spans\
         DROP INDEX trace_id,
         ADD UNIQUE KEY(`trace_id_high`, `trace_id`, `id`);
      ALTER TABLE zipkin_annotations
         DROP INDEX trace_id,
         ADD UNIQUE KEY(`trace_id_high`, `trace_id`, `span_id`, `a_key`, `a_timestamp`);\
      """;

  static boolean test(DataSource datasource, DSLContexts context) {
    try (Connection conn = datasource.getConnection()) {
      DSLContext dsl = context.get(conn);
      dsl.select(ZIPKIN_SPANS.TRACE_ID_HIGH).from(ZIPKIN_SPANS).limit(1).fetchAny();
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
    LOG.log(Level.WARNING, "problem reading zipkin_spans.trace_id_high", e);
  }
}
