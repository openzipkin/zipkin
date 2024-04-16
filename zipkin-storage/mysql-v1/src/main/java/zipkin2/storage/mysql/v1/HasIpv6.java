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

import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

final class HasIpv6 {
  private static final Logger LOG = Logger.getLogger(HasIpv6.class.getName());

  static boolean test(DataSource datasource, DSLContexts context) {
    try (Connection conn = datasource.getConnection()) {
      DSLContext dsl = context.get(conn);
      dsl.select(ZIPKIN_ANNOTATIONS.ENDPOINT_IPV6).from(ZIPKIN_ANNOTATIONS).limit(1).fetchAny();
      return true;
    } catch (DataAccessException e) {
      if (e.sqlState().equals("42S22")) {
        LOG.warning(
            """
            zipkin_annotations.ipv6 doesn't exist, so Endpoint.ipv6 is not supported. \
            Execute: alter table zipkin_annotations add `endpoint_ipv6` BINARY(16)\
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
    LOG.log(Level.WARNING, "problem reading zipkin_annotations.ipv6", e);
  }
}
