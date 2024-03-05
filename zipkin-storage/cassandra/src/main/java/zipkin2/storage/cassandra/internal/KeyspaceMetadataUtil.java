/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import java.util.Optional;

public final class KeyspaceMetadataUtil {

  public static int getDefaultTtl(KeyspaceMetadata keyspaceMetadata, String table) {
    return (int) keyspaceMetadata.getTable(table)
      .map(TableMetadata::getOptions)
      .flatMap(o -> Optional.ofNullable(o.get(CqlIdentifier.fromCql("default_time_to_live"))))
      .orElse(0);
  }

  KeyspaceMetadataUtil() {
  }
}
