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
package zipkin.storage.cassandra;

import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TypeCodec;
import java.nio.ByteBuffer;

final class TimestampCodec {
  private final ProtocolVersion protocolVersion;

  TimestampCodec(ProtocolVersion protocolVersion) {
    this.protocolVersion = protocolVersion;
  }

  public TimestampCodec(Session session) {
    this(session.getCluster().getConfiguration().getProtocolOptions().getProtocolVersion());
  }

  /**
   * Truncates timestamp to milliseconds and converts to binary for using with setBytesUnsafe() to
   * avoid allocating java.util.Date
   */
  public ByteBuffer serialize(long timestamp) {
    return TypeCodec.bigint().serialize(timestamp / 1000L, protocolVersion);
  }

  /**
   * Reads timestamp binary value directly (getBytesUnsafe) to avoid allocating java.util.Date, and
   * converts to microseconds.
   */
  public long deserialize(Row row, String name) {
    return 1000L * TypeCodec.bigint().deserialize(row.getBytesUnsafe(name), protocolVersion);
  }
}
