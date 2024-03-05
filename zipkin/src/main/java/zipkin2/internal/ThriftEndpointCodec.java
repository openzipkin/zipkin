/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import zipkin2.Endpoint;

import static zipkin2.internal.ThriftCodec.skip;
import static zipkin2.internal.ThriftField.TYPE_I16;
import static zipkin2.internal.ThriftField.TYPE_I32;
import static zipkin2.internal.ThriftField.TYPE_STOP;
import static zipkin2.internal.ThriftField.TYPE_STRING;
import static zipkin2.internal.WriteBuffer.utf8SizeInBytes;

final class ThriftEndpointCodec {
  static final byte[] INT_ZERO = {0, 0, 0, 0};
  static final ThriftField IPV4 = new ThriftField(TYPE_I32, 1);
  static final ThriftField PORT = new ThriftField(TYPE_I16, 2);
  static final ThriftField SERVICE_NAME = new ThriftField(TYPE_STRING, 3);
  static final ThriftField IPV6 = new ThriftField(TYPE_STRING, 4);

  static Endpoint read(ReadBuffer buffer) {
    Endpoint.Builder result = Endpoint.newBuilder();

    while (true) {
      ThriftField thriftField = ThriftField.read(buffer);
      if (thriftField.type == TYPE_STOP) break;

      if (thriftField.isEqualTo(IPV4)) {
        int ipv4 = buffer.readInt();
        if (ipv4 != 0) {
          result.parseIp( // allocation is ok here as Endpoint.ipv4Bytes would anyway
            new byte[] {
              (byte) (ipv4 >> 24 & 0xff),
              (byte) (ipv4 >> 16 & 0xff),
              (byte) (ipv4 >> 8 & 0xff),
              (byte) (ipv4 & 0xff)
            });
        }
      } else if (thriftField.isEqualTo(PORT)) {
        result.port(buffer.readShort() & 0xFFFF);
      } else if (thriftField.isEqualTo(SERVICE_NAME)) {
        result.serviceName(buffer.readUtf8(buffer.readInt()));
      } else if (thriftField.isEqualTo(IPV6)) {
        result.parseIp(buffer.readBytes(buffer.readInt()));
      } else {
        skip(buffer, thriftField.type);
      }
    }
    return result.build();
  }

  static int sizeInBytes(Endpoint value) {
    String serviceName = value.serviceName();
    int sizeInBytes = 0;
    sizeInBytes += 3 + 4; // IPV4
    sizeInBytes += 3 + 2; // PORT
    sizeInBytes += 3 + 4 + (serviceName != null ? utf8SizeInBytes(serviceName) : 0);
    if (value.ipv6() != null) sizeInBytes += 3 + 4 + 16;
    sizeInBytes++; // TYPE_STOP
    return sizeInBytes;
  }

  static void write(Endpoint value, WriteBuffer buffer) {
    IPV4.write(buffer);
    buffer.write(value.ipv4Bytes() != null ? value.ipv4Bytes() : INT_ZERO);

    PORT.write(buffer);
    int port = value.portAsInt();
    // write short!
    buffer.writeByte((port >>> 8L) & 0xff);
    buffer.writeByte(port & 0xff);

    SERVICE_NAME.write(buffer);
    ThriftCodec.writeLengthPrefixed(buffer, value.serviceName() != null ? value.serviceName() : "");

    byte[] ipv6 = value.ipv6Bytes();
    if (ipv6 != null) {
      IPV6.write(buffer);
      ThriftCodec.writeInt(buffer, 16);
      buffer.write(ipv6);
    }

    buffer.writeByte(TYPE_STOP);
  }
}
