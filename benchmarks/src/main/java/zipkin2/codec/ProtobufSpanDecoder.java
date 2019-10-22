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
package zipkin2.codec;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Platform;

public class ProtobufSpanDecoder {
  static final Logger LOG = Logger.getLogger(ProtobufSpanDecoder.class.getName());
  static final boolean DEBUG = false;

  // map<string,string> in proto is a special field with key, value
  static final int MAP_KEY_KEY = (1 << 3) | WireFormat.WIRETYPE_LENGTH_DELIMITED;
  static final int MAP_VALUE_KEY = (2 << 3) | WireFormat.WIRETYPE_LENGTH_DELIMITED;

  static boolean decodeTag(CodedInputStream input, Span.Builder span) throws IOException {
    // now, we are in the tag fields
    String key = null, value = ""; // empty tags allowed

    boolean done = false;
    while (!done) {
      int tag = input.readTag();
      switch (tag) {
        case 0:
          done = true;
          break;
        case MAP_KEY_KEY: {
          key = input.readStringRequireUtf8();
          break;
        }
        case MAP_VALUE_KEY: {
          value = input.readStringRequireUtf8();
          break;
        }
        default: {
          logAndSkip(input, tag);
          break;
        }
      }
    }

    if (key == null) return false;
    span.putTag(key, value);
    return true;
  }

  static boolean decodeAnnotation(CodedInputStream input, Span.Builder span) throws IOException {
    long timestamp = 0L;
    String value = null;

    boolean done = false;
    while (!done) {
      int tag = input.readTag();
      switch (tag) {
        case 0:
          done = true;
          break;
        case 9: {
          timestamp = input.readFixed64();
          break;
        }
        case 18: {
          value = input.readStringRequireUtf8();
          break;
        }
        default: {
          logAndSkip(input, tag);
          break;
        }
      }
    }

    if (timestamp == 0L || value == null) return false;
    span.addAnnotation(timestamp, value);
    return true;
  }

  private static Endpoint decodeEndpoint(CodedInputStream input) throws IOException {
    Endpoint.Builder endpoint = Endpoint.newBuilder();

    boolean done = false;
    while (!done) {
      int tag = input.readTag();
      switch (tag) {
        case 0:
          done = true;
          break;
        case 10: {
          endpoint.serviceName(input.readStringRequireUtf8());
          break;
        }
        case 18:
        case 26: {
          endpoint.parseIp(input.readByteArray());
          break;
        }
        case 32: {
          endpoint.port(input.readInt32());
          break;
        }
        default: {
          logAndSkip(input, tag);
          break;
        }
      }
    }
    return endpoint.build();
  }

  public static Span decodeOne(CodedInputStream input) throws IOException {
    Span.Builder span = Span.newBuilder();

    boolean done = false;
    while (!done) {
      int tag = input.readTag();
      switch (tag) {
        case 0:
          done = true;
          break;
        case 10: {
          span.traceId(readHexString(input));
          break;
        }
        case 18: {
          span.parentId(readHexString(input));
          break;
        }
        case 26: {
          span.id(readHexString(input));
          break;
        }
        case 32: {
          int kind = input.readEnum();
          if (kind == 0) break;
          if (kind > Span.Kind.values().length) break;
          span.kind(Span.Kind.values()[kind - 1]);
          break;
        }
        case 42: {
          span.name(input.readStringRequireUtf8());
          break;
        }
        case 49: {
          span.timestamp(input.readFixed64());
          break;
        }
        case 56: {
          span.duration(input.readUInt64());
          break;
        }
        case 66: {
          int length = input.readRawVarint32();
          int oldLimit = input.pushLimit(length);

          span.localEndpoint(decodeEndpoint(input));

          input.checkLastTagWas(0);
          input.popLimit(oldLimit);
          break;
        }
        case 74: {
          int length = input.readRawVarint32();
          int oldLimit = input.pushLimit(length);

          span.remoteEndpoint(decodeEndpoint(input));

          input.checkLastTagWas(0);
          input.popLimit(oldLimit);
          break;
        }
        case 82: {
          int length = input.readRawVarint32();
          int oldLimit = input.pushLimit(length);

          decodeAnnotation(input, span);

          input.checkLastTagWas(0);
          input.popLimit(oldLimit);
          break;
        }
        case 90: {
          int length = input.readRawVarint32();
          int oldLimit = input.pushLimit(length);

          decodeTag(input, span);

          input.checkLastTagWas(0);
          input.popLimit(oldLimit);
          break;
        }
        case 96: {
          span.debug(input.readBool());
          break;
        }
        case 104: {
          span.shared(input.readBool());
          break;
        }
        default: {
          logAndSkip(input, tag);
          break;
        }
      }
    }

    return span.build();
  }

  public static List<Span> decodeList(byte[] spans) {
    return decodeList(CodedInputStream.newInstance(spans));
  }

  public static List<Span> decodeList(ByteBuffer spans) {
    return decodeList(CodedInputStream.newInstance(spans));
  }

  public static List<Span> decodeList(CodedInputStream input) {
    ArrayList<Span> spans = new ArrayList<>();

    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          case 10:
            int length = input.readRawVarint32();
            int oldLimit = input.pushLimit(length);
            spans.add(decodeOne(input));
            input.checkLastTagWas(0);
            input.popLimit(oldLimit);
            break;
          default: {
            logAndSkip(input, tag);
            break;
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return spans;
  }

  static final char[] HEX_DIGITS =
    {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  private static String readHexString(CodedInputStream input) throws IOException {
    int size = input.readRawVarint32();
    int length = size * 2;

    // All our hex fields are at most 32 characters.
    if (length > 32) {
      throw new AssertionError("hex field greater than 32 chars long: " + length);
    }

    char[] result = Platform.shortStringBuffer();

    for (int i = 0; i < length; i += 2) {
      byte b = input.readRawByte();
      result[i] = HEX_DIGITS[(b >> 4) & 0xf];
      result[i + 1] = HEX_DIGITS[b & 0xf];
    }

    return new String(result, 0, length);
  }

  static void logAndSkip(CodedInputStream input, int tag) throws IOException {
    if (DEBUG) { // avoiding volatile reads as we don't log on skip in our normal codec
      int nextWireType = WireFormat.getTagWireType(tag);
      int nextFieldNumber = WireFormat.getTagFieldNumber(tag);
      LOG.fine(String.format("Skipping field: byte=%s, fieldNumber=%s, wireType=%s",
        input.getTotalBytesRead(), nextFieldNumber, nextWireType));
    }
    input.skipField(tag);
  }
}
