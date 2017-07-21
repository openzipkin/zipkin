/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
import org.jooq.DSLContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import zipkin.internal.Nullable;

import static zipkin.internal.Util.checkNotNull;

final class DSLContexts {
  private final Settings settings;
  private final ExecuteListenerProvider listenerProvider;

  DSLContexts(Settings settings, @Nullable ExecuteListenerProvider listenerProvider) {
    this.settings = checkNotNull(settings, "settings");
    this.listenerProvider = listenerProvider;
  }

  DSLContext get(Connection conn) {
    return DSL.using(new DefaultConfiguration()
        .set(conn)
        .set(SQLDialect.MYSQL)
        .set(settings)
        .set(listenerProvider));
  }
}
