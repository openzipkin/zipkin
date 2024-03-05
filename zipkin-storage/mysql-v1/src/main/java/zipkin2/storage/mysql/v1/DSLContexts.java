/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.sql.Connection;
import org.jooq.DSLContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import zipkin2.internal.Nullable;

final class DSLContexts {
  private final Settings settings;
  private final ExecuteListenerProvider listenerProvider;

  DSLContexts(Settings settings, @Nullable ExecuteListenerProvider listenerProvider) {
    this.settings = settings;
    this.listenerProvider = listenerProvider;
  }

  DSLContext get(Connection conn) {
    return DSL.using(
        new DefaultConfiguration()
            .set(conn)
            .set(SQLDialect.MYSQL)
            .set(settings)
            .set(listenerProvider));
  }
}
