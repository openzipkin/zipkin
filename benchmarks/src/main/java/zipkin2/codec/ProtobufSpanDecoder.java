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

import static java.util.logging.Level.FINE;

public class ProtobufSpanDecoder {
  static final Logger LOG = Logger.getLogger(ProtobufSpanDecoder.class.getName());

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
          java.lang.String s = input.readStringRequireUtf8();
          value = s;
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
          java.lang.String s = input.readStringRequireUtf8();
          endpoint.serviceName(s);
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
          java.lang.String name = input.readStringRequireUtf8();
          span.name(name);
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

    char[] result = new char[size * 2];

    for (int i = 0; i < result.length; i += 2) {
      byte b = input.readRawByte();
      result[i] = HEX_DIGITS[(b >> 4) & 0xf];
      result[i + 1] = HEX_DIGITS[b & 0xf];
    }

    return new String(result);
  }



  static void logAndSkip(CodedInputStream input, int tag) throws IOException {
    int nextWireType = WireFormat.getTagWireType(tag);
    if (LOG.isLoggable(FINE)) {
      int nextFieldNumber = WireFormat.getTagFieldNumber(tag);
      LOG.fine(String.format("Skipping field: byte=%s, fieldNumber=%s, wireType=%s",
        input.getTotalBytesRead(), nextFieldNumber, nextWireType));
    }
    input.skipField(tag);
  }
}
