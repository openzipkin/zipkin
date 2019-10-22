/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.internal;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import zipkin2.DependencyLink;

import static zipkin2.internal.ThriftCodec.skip;
import static zipkin2.internal.ThriftField.TYPE_I64;
import static zipkin2.internal.ThriftField.TYPE_LIST;
import static zipkin2.internal.ThriftField.TYPE_STOP;
import static zipkin2.internal.ThriftField.TYPE_STRING;
import static zipkin2.internal.WriteBuffer.utf8SizeInBytes;

/**
 * Internal as only cassandra serializes the start and end timestamps along with link data, and
 * those serialized timestamps are never read.
 *
 * @deprecated See https://github.com/openzipkin/zipkin/issues/1008
 */
public final class Dependencies {
  static final ThriftField START_TS = new ThriftField(TYPE_I64, 1);
  static final ThriftField END_TS = new ThriftField(TYPE_I64, 2);
  static final ThriftField LINKS = new ThriftField(TYPE_LIST, 3);
  static final DependencyLinkAdapter DEPENDENCY_LINK_ADAPTER = new DependencyLinkAdapter();

  public List<DependencyLink> links() {
    return links;
  }

  /** Reads from buffer serialized in TBinaryProtocol */
  public static Dependencies fromThrift(ByteBuffer bytes) {
    long startTs = 0L;
    long endTs = 0L;
    List<DependencyLink> links = Collections.emptyList();

    ReadBuffer buffer = ReadBuffer.wrapUnsafe(bytes);
    while (true) {
      ThriftField thriftField = ThriftField.read(buffer);
      if (thriftField.type == TYPE_STOP) break;

      if (thriftField.isEqualTo(START_TS)) {
        startTs = buffer.readLong();
      } else if (thriftField.isEqualTo(END_TS)) {
        endTs = buffer.readLong();
      } else if (thriftField.isEqualTo(LINKS)) {
        int length = ThriftCodec.readListLength(buffer);
        if (length == 0) continue;
        links = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
          links.add(DependencyLinkAdapter.read(buffer));
        }
      } else {
        skip(buffer, thriftField.type);
      }
    }

    return Dependencies.create(startTs, endTs, links);
  }

  /** Writes the current instance in TBinaryProtocol */
  public ByteBuffer toThrift() {
    byte[] result = new byte[sizeInBytes()];
    write(WriteBuffer.wrap(result));
    return ByteBuffer.wrap(result);
  }

  int sizeInBytes() {
    int sizeInBytes = 0;
    sizeInBytes += 3 + 8; // START_TS
    sizeInBytes += 3 + 8; // END_TS
    sizeInBytes += 3 + ThriftCodec.listSizeInBytes(DEPENDENCY_LINK_ADAPTER, links);
    sizeInBytes++; // TYPE_STOP
    return sizeInBytes;
  }

  void write(WriteBuffer buffer) {
    START_TS.write(buffer);
    ThriftCodec.writeLong(buffer, startTs);

    END_TS.write(buffer);
    ThriftCodec.writeLong(buffer, endTs);

    LINKS.write(buffer);
    ThriftCodec.writeList(DEPENDENCY_LINK_ADAPTER, links, buffer);

    buffer.writeByte(TYPE_STOP);
  }

  /** timestamps are in epoch milliseconds */
  public static Dependencies create(long startTs, long endTs, List<DependencyLink> links) {
    return new Dependencies(startTs, endTs, links);
  }

  final long startTs, endTs;
  final List<DependencyLink> links;

  Dependencies(long startTs, long endTs, List<DependencyLink> links) {
    this.startTs = startTs;
    this.endTs = endTs;
    if (links == null) throw new NullPointerException("links == null");
    this.links = links;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Dependencies)) return false;
    Dependencies that = (Dependencies) o;
    return startTs == that.startTs && endTs == that.endTs && links.equals(that.links);
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

  static final class DependencyLinkAdapter implements WriteBuffer.Writer<DependencyLink> {

    static final ThriftField PARENT = new ThriftField(TYPE_STRING, 1);
    static final ThriftField CHILD = new ThriftField(TYPE_STRING, 2);
    static final ThriftField CALL_COUNT = new ThriftField(TYPE_I64, 4);
    static final ThriftField ERROR_COUNT = new ThriftField(TYPE_I64, 5);

    static DependencyLink read(ReadBuffer buffer) {
      DependencyLink.Builder result = DependencyLink.newBuilder();
      ThriftField thriftField;

      while (true) {
        thriftField = ThriftField.read(buffer);
        if (thriftField.type == TYPE_STOP) break;

        if (thriftField.isEqualTo(PARENT)) {
          result.parent(buffer.readUtf8(buffer.readInt()));
        } else if (thriftField.isEqualTo(CHILD)) {
          result.child(buffer.readUtf8(buffer.readInt()));
        } else if (thriftField.isEqualTo(CALL_COUNT)) {
          result.callCount(buffer.readLong());
        } else if (thriftField.isEqualTo(ERROR_COUNT)) {
          result.errorCount(buffer.readLong());
        } else {
          skip(buffer, thriftField.type);
        }
      }

      return result.build();
    }

    @Override public int sizeInBytes(DependencyLink value) {
      int sizeInBytes = 0;
      sizeInBytes += 3 + 4 + utf8SizeInBytes(value.parent());
      sizeInBytes += 3 + 4 + utf8SizeInBytes(value.child());
      sizeInBytes += 3 + 8; // CALL_COUNT
      if (value.errorCount() > 0) sizeInBytes += 3 + 8; // ERROR_COUNT
      sizeInBytes++; // TYPE_STOP
      return sizeInBytes;
    }

    @Override public void write(DependencyLink value, WriteBuffer buffer) {
      PARENT.write(buffer);
      ThriftCodec.writeLengthPrefixed(buffer, value.parent());

      CHILD.write(buffer);
      ThriftCodec.writeLengthPrefixed(buffer, value.child());

      CALL_COUNT.write(buffer);
      ThriftCodec.writeLong(buffer, value.callCount());

      if (value.errorCount() > 0) {
        ERROR_COUNT.write(buffer);
        ThriftCodec.writeLong(buffer, value.errorCount());
      }

      buffer.writeByte(TYPE_STOP);
    }
  }
}
