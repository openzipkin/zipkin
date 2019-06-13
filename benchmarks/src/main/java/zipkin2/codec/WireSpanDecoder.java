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

import com.google.protobuf.WireFormat;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.ProtoReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import okio.Buffer;
import okio.ByteString;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Platform;

public class WireSpanDecoder {
  static final Logger LOG = Logger.getLogger(WireSpanDecoder.class.getName());
  static final boolean DEBUG = false;

  static boolean decodeTag(ProtoReader input, Span.Builder span) throws IOException {
    // now, we are in the tag fields
    String key = null, value = ""; // empty tags allowed

    boolean done = false;
    while (!done) {
      int tag = input.nextTag();
      switch (tag) {
        case -1:
          done = true;
          break;
        case 1: {
          key = input.readString();
          break;
        }
        case 2: {
          value = input.readString();
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

  static boolean decodeAnnotation(ProtoReader input, Span.Builder span) throws IOException {
    long timestamp = 0L;
    String value = null;

    boolean done = false;
    while (!done) {
      int tag = input.nextTag();
      switch (tag) {
        case -1:
          done = true;
          break;
        case 1: {
          timestamp = input.readFixed64();
          break;
        }
        case 2: {
          value = input.readString();
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

  private static Endpoint decodeEndpoint(ProtoReader input) throws IOException {
    Endpoint.Builder endpoint = Endpoint.newBuilder();

    boolean done = false;
    while (!done) {
      int tag = input.nextTag();
      switch (tag) {
        case -1:
          done = true;
          break;
        case 1: {
          String s = input.readString();
          endpoint.serviceName(s);
          break;
        }
        case 2:
        case 3: {
          endpoint.parseIp(input.readBytes().toByteArray());
          break;
        }
        case 4: {
          endpoint.port(input.readVarint32());
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

  public static Span decodeOne(ProtoReader input) throws IOException {
    Span.Builder span = Span.newBuilder();

    boolean done = false;
    while (!done) {
      int tag = input.nextTag();
      switch (tag) {
        case -1:
          done = true;
          break;
        case 1: {
          span.traceId(readHexString(input));
          break;
        }
        case 2: {
          span.parentId(readHexString(input));
          break;
        }
        case 3: {
          span.id(readHexString(input));
          break;
        }
        case 4: {
          int kind = input.readVarint32();
          if (kind == 0) break;
          if (kind > Span.Kind.values().length) break;
          span.kind(Span.Kind.values()[kind - 1]);
          break;
        }
        case 5: {
          String name = input.readString();
          span.name(name);
          break;
        }
        case 6: {
          span.timestamp(input.readFixed64());
          break;
        }
        case 7: {
          span.duration(input.readVarint64());
          break;
        }
        case 8: {
          long token = input.beginMessage();

          span.localEndpoint(decodeEndpoint(input));

          input.endMessage(token);
          break;
        }
        case 9: {
          long token = input.beginMessage();

          span.remoteEndpoint(decodeEndpoint(input));

          input.endMessage(token);
          break;
        }
        case 10: {
          long token = input.beginMessage();

          decodeAnnotation(input, span);

          input.endMessage(token);
          break;
        }
        case 11: {
          long token = input.beginMessage();

          decodeTag(input, span);

          input.endMessage(token);
          break;
        }
        case 12: {
          span.debug(ProtoAdapter.BOOL.decode(input));
          break;
        }
        case 13: {
          span.shared(ProtoAdapter.BOOL.decode(input));
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
    return decodeList(new ProtoReader(new Buffer().write(spans)));
  }

  public static List<Span> decodeList(ByteBuffer spans) {
    Buffer buffer = new Buffer();
    try {
      buffer.write(spans);
    } catch (IOException e) {
      throw new AssertionError(e); // no I/O
    }
    return decodeList(new ProtoReader(buffer));
  }

  public static List<Span> decodeList(ProtoReader input) {
    ArrayList<Span> spans = new ArrayList<>();

    final long token;
    try {
      token = input.beginMessage();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    try {
      boolean done = false;
      while (!done) {
        int tag = input.nextTag();
        switch (tag) {
          case -1:
            done = true;
            break;
          case 1: {
            long subToken = input.beginMessage();

            spans.add(decodeOne(input));

            input.endMessage(subToken);
            break;
          }
          default: {
            logAndSkip(input, tag);
            break;
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      input.endMessage(token);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return spans;
  }

  static final char[] HEX_DIGITS =
    {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  // https://github.com/square/wire/issues/958
  private static String readHexString(ProtoReader input) throws IOException {
    ByteString bytes = input.readBytes();
    int length = bytes.size() * 2;

    // All our hex fields are at most 32 characters.
    if (length > 32) {
      throw new AssertionError("hex field greater than 32 chars long: " + length);
    }

    char[] result = Platform.shortStringBuffer();

    for (int i = 0; i < bytes.size(); i ++) {
      byte b = bytes.getByte(i);
      result[2 * i] = HEX_DIGITS[(b >> 4) & 0xf];
      result[2 * i + 1] = HEX_DIGITS[b & 0xf];
    }

    return new String(result, 0, length);
  }

  static void logAndSkip(ProtoReader input, int tag) throws IOException {
    if (DEBUG) { // avoiding volatile reads as we don't log on skip in our normal codec
      int nextWireType = WireFormat.getTagWireType(tag);
      int nextFieldNumber = WireFormat.getTagFieldNumber(tag);
      LOG.fine(String.format("Skipping field: byte=%s, fieldNumber=%s, wireType=%s",
        0, nextFieldNumber, nextWireType));
    }
    input.skip();
  }
}
