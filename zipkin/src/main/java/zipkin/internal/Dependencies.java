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
package zipkin.internal;

import java.nio.ByteBuffer;
import java.util.List;
import zipkin.DependencyLink;

import static zipkin.internal.ThriftCodec.DEPENDENCY_LINK_ADAPTER;
import static zipkin.internal.ThriftCodec.Field;
import static zipkin.internal.ThriftCodec.TYPE_I64;
import static zipkin.internal.ThriftCodec.TYPE_LIST;
import static zipkin.internal.ThriftCodec.TYPE_STOP;
import static zipkin.internal.ThriftCodec.ThriftAdapter;
import static zipkin.internal.ThriftCodec.read;
import static zipkin.internal.ThriftCodec.skip;
import static zipkin.internal.ThriftCodec.write;
import static zipkin.internal.Util.checkNotNull;

/**
 * Internal as only cassandra serializes the start and end timestamps along with link data, and
 * those serialized timestamps are never read.
 *
 * @deprecated See https://github.com/openzipkin/zipkin/issues/1008
 */
@Deprecated
public final class Dependencies {

  /** Reads from bytes serialized in TBinaryProtocol */
  public static Dependencies fromThrift(ByteBuffer bytes) {
    return read(THRIFT_ADAPTER, bytes);
  }

  /** Writes the current instance in TBinaryProtocol */
  public ByteBuffer toThrift() {
    return ByteBuffer.wrap(write(THRIFT_ADAPTER, this));
  }

  public static Dependencies create(long startTs, long endTs, List<DependencyLink> links) {
    return new Dependencies(startTs, endTs, links);
  }

  /** milliseconds from epoch */
  public final long startTs;

  /** milliseconds from epoch) */
  public final long endTs;

  /** link information for every dependent service */
  public final List<DependencyLink> links;

  Dependencies(long startTs, long endTs, List<DependencyLink> links) {
    this.startTs = startTs;
    this.endTs = endTs;
    this.links = checkNotNull(links, "links");
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Dependencies) {
      Dependencies that = (Dependencies) o;
      return (this.startTs == that.startTs)
          && (this.endTs == that.endTs)
          && (this.links.equals(that.links));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) (h ^ ((startTs >>> 32) ^ startTs));
    h *= 1000003;
    h ^= (int) (h ^ ((endTs >>> 32) ^ endTs));
    h *= 1000003;
    h ^= links.hashCode();
    return h;
  }

  /** @deprecated See https://github.com/openzipkin/zipkin/issues/1008 */
  @Deprecated
  static final ThriftAdapter<Dependencies> THRIFT_ADAPTER = new ThriftAdapter<Dependencies>() {

    final Field START_TS = new Field(TYPE_I64, 1);
    final Field END_TS = new Field(TYPE_I64, 2);
    final Field LINKS = new Field(TYPE_LIST, 3);

    @Override
    public Dependencies read(ByteBuffer bytes) {
      long startTs = 0L;
      long endTs = 0L;
      List<DependencyLink> links = null;

      Field field;

      while (true) {
        field = Field.read(bytes);
        if (field.type == TYPE_STOP) break;

        if (field.isEqualTo(START_TS)) {
          startTs = bytes.getLong();
        } else if (field.isEqualTo(END_TS)) {
          endTs = bytes.getLong();
        } else if (field.isEqualTo(LINKS)) {
          links = ThriftCodec.readList(DEPENDENCY_LINK_ADAPTER, bytes);
        } else {
          skip(bytes, field.type);
        }
      }

      return Dependencies.create(startTs, endTs, links);
    }

    @Override public int sizeInBytes(Dependencies value) {
      int sizeInBytes = 0;
      sizeInBytes += 3 + 8; // START_TS
      sizeInBytes += 3 + 8; // END_TS
      sizeInBytes += 3 + ThriftCodec.listSizeInBytes(DEPENDENCY_LINK_ADAPTER, value.links);
      sizeInBytes++; //TYPE_STOP
      return sizeInBytes;
    }

    @Override
    public void write(Dependencies value, Buffer buffer) {

      START_TS.write(buffer);
      buffer.writeLong(value.startTs);

      END_TS.write(buffer);
      buffer.writeLong(value.endTs);

      LINKS.write(buffer);
      ThriftCodec.writeList(DEPENDENCY_LINK_ADAPTER, value.links, buffer);

      buffer.writeByte(TYPE_STOP);
    }

    @Override
    public String toString() {
      return "Dependencies";
    }
  };
}
