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

import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.internal.v2.Endpoint;
import zipkin.internal.v2.Span;
import zipkin.internal.v2.Span.Kind;

import static zipkin.BinaryAnnotation.Type.BOOL;
import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.LOCAL_COMPONENT;
import static zipkin.Constants.SERVER_ADDR;
import static zipkin.internal.Util.lowerHexToUnsignedLong;
import static zipkin.internal.Util.writeBase64Url;

/**
 * This converts {@link zipkin.Span} instances to {@link Span} and visa versa.
 */
public final class V2SpanConverter {

  /**
   * Converts the input, parsing RPC annotations into {@link Span#kind()}.
   *
   * @return a span for each unique {@link Annotation#endpoint annotation endpoint} service name.
   */
  public static List<Span> fromSpan(zipkin.Span source) {
    Builders builders = new Builders(source);
    // add annotations unless they are "core"
    builders.processAnnotations(source);
    // convert binary annotations to tags and addresses
    builders.processBinaryAnnotations(source);
    return builders.build();
  }

  static final class Builders {
    final List<Span.Builder> spans = new ArrayList<>();
    Annotation cs = null, sr = null, ss = null, cr = null, ms = null, mr = null, ws = null, wr =
      null;

    Builders(zipkin.Span source) {
      this.spans.add(newBuilder(source));
    }

    void processAnnotations(zipkin.Span source) {
      for (int i = 0, length = source.annotations.size(); i < length; i++) {
        Annotation a = source.annotations.get(i);
        Span.Builder currentSpan = forEndpoint(source, a.endpoint);
        // core annotations require an endpoint. Don't give special treatment when that's missing
        if (a.value.length() == 2 && a.endpoint != null) {
          if (a.value.equals(Constants.CLIENT_SEND)) {
            currentSpan.kind(Kind.CLIENT);
            cs = a;
          } else if (a.value.equals(Constants.SERVER_RECV)) {
            currentSpan.kind(Kind.SERVER);
            sr = a;
          } else if (a.value.equals(Constants.SERVER_SEND)) {
            currentSpan.kind(Kind.SERVER);
            ss = a;
          } else if (a.value.equals(Constants.CLIENT_RECV)) {
            currentSpan.kind(Kind.CLIENT);
            cr = a;
          } else if (a.value.equals(Constants.MESSAGE_SEND)) {
            currentSpan.kind(Kind.PRODUCER);
            ms = a;
          } else if (a.value.equals(Constants.MESSAGE_RECV)) {
            currentSpan.kind(Kind.CONSUMER);
            mr = a;
          } else if (a.value.equals(Constants.WIRE_SEND)) {
            ws = a;
          } else if (a.value.equals(Constants.WIRE_RECV)) {
            wr = a;
          } else {
            currentSpan.addAnnotation(a.timestamp, a.value);
          }
        } else {
          currentSpan.addAnnotation(a.timestamp, a.value);
        }
      }

      if (cs != null && sr != null) {
        // in a shared span, the client side owns span duration by annotations or explicit timestamp
        maybeTimestampDuration(source, cs, cr);

        // special-case loopback: We need to make sure on loopback there are two span2s
        Span.Builder client = forEndpoint(source, cs.endpoint);
        Span.Builder server;
        if (closeEnough(cs.endpoint, sr.endpoint)) {
          client.kind(Kind.CLIENT);
          // fork a new span for the server side
          server = newSpanBuilder(source, convert(sr.endpoint)).kind(Kind.SERVER);
        } else {
          server = forEndpoint(source, sr.endpoint);
        }

        // the server side is smaller than that, we have to read annotations to find out
        server.shared(true).timestamp(sr.timestamp);
        if (ss != null) server.duration(ss.timestamp - sr.timestamp);
        if (cr == null && source.duration == null) client.duration(null); // one-way has no duration
      } else if (cs != null && cr != null) {
        maybeTimestampDuration(source, cs, cr);
      } else if (sr != null && ss != null) {
        maybeTimestampDuration(source, sr, ss);
      } else { // otherwise, the span is incomplete. revert special-casing
        for (Span.Builder next : spans) {
          if (Kind.CLIENT.equals(next.kind())) {
            if (cs != null) next.timestamp(cs.timestamp);
          } else if (Kind.SERVER.equals(next.kind())) {
            if (sr != null) next.timestamp(sr.timestamp);
          }
        }

        if (source.timestamp != null) {
          spans.get(0).timestamp(source.timestamp).duration(source.duration);
        }
      }

      // Span v1 format did not have a shared flag. By convention, span.timestamp being absent
      // implied shared. When we only see the server-side, carry this signal over.
      if (cs == null && (sr != null && source.timestamp == null)) {
        forEndpoint(source, sr.endpoint).shared(true);
      }

      // ms and mr are not supposed to be in the same span, but in case they are..
      if (ms != null && mr != null) {
        // special-case loopback: We need to make sure on loopback there are two span2s
        Span.Builder producer = forEndpoint(source, ms.endpoint);
        Span.Builder consumer;
        if (closeEnough(ms.endpoint, mr.endpoint)) {
          producer.kind(Kind.PRODUCER);
          // fork a new span for the consumer side
          consumer = newSpanBuilder(source, convert(mr.endpoint)).kind(Kind.CONSUMER);
        } else {
          consumer = forEndpoint(source, mr.endpoint);
        }

        consumer.shared(true);
        if (wr != null) {
          consumer.timestamp(wr.timestamp).duration(mr.timestamp - wr.timestamp);
        } else {
          consumer.timestamp(mr.timestamp);
        }

        producer.timestamp(ms.timestamp).duration(ws != null ? ws.timestamp - ms.timestamp : null);
      } else if (ms != null) {
        maybeTimestampDuration(source, ms, ws);
      } else if (mr != null) {
        if (wr != null) {
          maybeTimestampDuration(source, wr, mr);
        } else {
          maybeTimestampDuration(source, mr, null);
        }
      } else {
        if (ws != null) forEndpoint(source, ws.endpoint).addAnnotation(ws.timestamp, ws.value);
        if (wr != null) forEndpoint(source, wr.endpoint).addAnnotation(wr.timestamp, wr.value);
      }
    }

    void maybeTimestampDuration(zipkin.Span source, Annotation begin, @Nullable Annotation end) {
      Span.Builder span2 = forEndpoint(source, begin.endpoint);
      if (source.timestamp != null && source.duration != null) {
        span2.timestamp(source.timestamp).duration(source.duration);
      } else {
        span2.timestamp(begin.timestamp);
        if (end != null) span2.duration(end.timestamp - begin.timestamp);
      }
    }

    void processBinaryAnnotations(zipkin.Span source) {
      zipkin.Endpoint ca = null, sa = null, ma = null;
      for (int i = 0, length = source.binaryAnnotations.size(); i < length; i++) {
        BinaryAnnotation b = source.binaryAnnotations.get(i);
        if (b.type == BOOL) {
          if (Constants.CLIENT_ADDR.equals(b.key)) {
            ca = b.endpoint;
          } else if (Constants.SERVER_ADDR.equals(b.key)) {
            sa = b.endpoint;
          } else if (Constants.MESSAGE_ADDR.equals(b.key)) {
            ma = b.endpoint;
          } else {
            forEndpoint(source, b.endpoint).putTag(b.key, b.value[0] == 1 ? "true" : "false");
          }
          continue;
        }

        Span.Builder currentSpan = forEndpoint(source, b.endpoint);
        switch (b.type) {
          case BOOL:
            break; // already handled
          case STRING:
            // don't add marker "lc" tags
            if (Constants.LOCAL_COMPONENT.equals(b.key) && b.value.length == 0) continue;
            currentSpan.putTag(b.key, new String(b.value, Util.UTF_8));
            break;
          case BYTES:
            currentSpan.putTag(b.key, writeBase64Url(b.value));
            break;
          case I16:
            currentSpan.putTag(b.key, Short.toString(ByteBuffer.wrap(b.value).getShort()));
            break;
          case I32:
            currentSpan.putTag(b.key, Integer.toString(ByteBuffer.wrap(b.value).getInt()));
            break;
          case I64:
            currentSpan.putTag(b.key, Long.toString(ByteBuffer.wrap(b.value).getLong()));
            break;
          case DOUBLE:
            double wrapped = Double.longBitsToDouble(ByteBuffer.wrap(b.value).getLong());
            currentSpan.putTag(b.key, Double.toString(wrapped));
            break;
        }
      }

      if (cs != null && sa != null && !closeEnough(sa, cs.endpoint)) {
        forEndpoint(source, cs.endpoint).remoteEndpoint(convert(sa));
      }

      if (sr != null && ca != null && !closeEnough(ca, sr.endpoint)) {
        forEndpoint(source, sr.endpoint).remoteEndpoint(convert(ca));
      }

      if (ms != null && ma != null && !closeEnough(ma, ms.endpoint)) {
        forEndpoint(source, ms.endpoint).remoteEndpoint(convert(ma));
      }

      if (mr != null && ma != null && !closeEnough(ma, mr.endpoint)) {
        forEndpoint(source, mr.endpoint).remoteEndpoint(convert(ma));
      }

      // special-case when we are missing core annotations, but we have both address annotations
      if ((cs == null && sr == null) && (ca != null && sa != null)) {
        forEndpoint(source, ca).remoteEndpoint(convert(sa));
      }
    }

    Span.Builder forEndpoint(zipkin.Span source, @Nullable zipkin.Endpoint e) {
      if (e == null) return spans.get(0); // allocate missing endpoint data to first span
      Endpoint converted = convert(e);
      for (int i = 0, length = spans.size(); i < length; i++) {
        Span.Builder next = spans.get(i);
        Endpoint nextLocalEndpoint = next.localEndpoint();
        if (nextLocalEndpoint == null) {
          next.localEndpoint(converted);
          return next;
        } else if (closeEnough(convert(nextLocalEndpoint), e)) {
          return next;
        }
      }
      return newSpanBuilder(source, converted);
    }

    Span.Builder newSpanBuilder(zipkin.Span source, Endpoint e) {
      Span.Builder result = newBuilder(source).localEndpoint(e);
      spans.add(result);
      return result;
    }

    List<Span> build() {
      int length = spans.size();
      if (length == 1) return Collections.singletonList(spans.get(0).build());
      List<Span> result = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        result.add(spans.get(i).build());
      }
      return result;
    }
  }

  static boolean closeEnough(zipkin.Endpoint left, zipkin.Endpoint right) {
    return left.serviceName.equals(right.serviceName);
  }

  static Span.Builder newBuilder(zipkin.Span source) {
    return Span.newBuilder()
      .traceId(source.traceIdString())
      .parentId(source.parentId != null ? Util.toLowerHex(source.parentId) : null)
      .id(Util.toLowerHex(source.id))
      .name(source.name)
      .debug(source.debug);
  }

  /** Converts the input, parsing {@link Span#kind()} into RPC annotations. */
  public static zipkin.Span toSpan(Span in) {
    String traceId = in.traceId();
    zipkin.Span.Builder result = zipkin.Span.builder()
      .traceId(lowerHexToUnsignedLong(traceId))
      .parentId(in.parentId() != null ? lowerHexToUnsignedLong(in.parentId()) : null)
      .id(lowerHexToUnsignedLong(in.id()))
      .debug(in.debug())
      .name(in.name() != null ? in.name() : ""); // avoid a NPE

    if (traceId.length() == 32) {
      result.traceIdHigh(lowerHexToUnsignedLong(traceId, 0));
    }

    long startTs = in.timestamp() == null ? 0L : in.timestamp();
    Long endTs = in.duration() == null ? 0L : in.timestamp() + in.duration();
    if (startTs != 0L) {
      result.timestamp(startTs);
      result.duration(in.duration());
    }

    zipkin.Endpoint local = in.localEndpoint() != null ? convert(in.localEndpoint()) : null;
    zipkin.Endpoint remote = in.remoteEndpoint() != null ? convert(in.remoteEndpoint()) : null;
    Kind kind = in.kind();
    Annotation
      cs = null, sr = null, ss = null, cr = null, ms = null, mr = null, ws = null, wr = null;
    String remoteEndpointType = null;

    boolean wroteEndpoint = false;

    for (int i = 0, length = in.annotations().size(); i < length; i++) {
      zipkin.internal.v2.Annotation input = in.annotations().get(i);
      Annotation a = Annotation.create(input.timestamp(), input.value(), local);
      if (a.value.length() == 2) {
        if (a.value.equals(Constants.CLIENT_SEND)) {
          kind = Kind.CLIENT;
          cs = a;
          remoteEndpointType = SERVER_ADDR;
        } else if (a.value.equals(Constants.SERVER_RECV)) {
          kind = Kind.SERVER;
          sr = a;
          remoteEndpointType = CLIENT_ADDR;
        } else if (a.value.equals(Constants.SERVER_SEND)) {
          kind = Kind.SERVER;
          ss = a;
        } else if (a.value.equals(Constants.CLIENT_RECV)) {
          kind = Kind.CLIENT;
          cr = a;
        } else if (a.value.equals(Constants.MESSAGE_SEND)) {
          kind = Kind.PRODUCER;
          ms = a;
        } else if (a.value.equals(Constants.MESSAGE_RECV)) {
          kind = Kind.CONSUMER;
          mr = a;
        } else if (a.value.equals(Constants.WIRE_SEND)) {
          ws = a;
        } else if (a.value.equals(Constants.WIRE_RECV)) {
          wr = a;
        } else {
          wroteEndpoint = true;
          result.addAnnotation(a);
        }
      } else {
        wroteEndpoint = true;
        result.addAnnotation(a);
      }
    }

    if (kind != null) {
      switch (kind) {
        case CLIENT:
          remoteEndpointType = Constants.SERVER_ADDR;
          if (startTs != 0L) cs = Annotation.create(startTs, Constants.CLIENT_SEND, local);
          if (endTs != 0L) cr = Annotation.create(endTs, Constants.CLIENT_RECV, local);
          break;
        case SERVER:
          remoteEndpointType = Constants.CLIENT_ADDR;
          if (startTs != 0L) sr = Annotation.create(startTs, Constants.SERVER_RECV, local);
          if (endTs != 0L) ss = Annotation.create(endTs, Constants.SERVER_SEND, local);
          break;
        case PRODUCER:
          remoteEndpointType = Constants.MESSAGE_ADDR;
          if (startTs != 0L) ms = Annotation.create(startTs, Constants.MESSAGE_SEND, local);
          if (endTs != 0L) ws = Annotation.create(endTs, Constants.WIRE_SEND, local);
          break;
        case CONSUMER:
          remoteEndpointType = Constants.MESSAGE_ADDR;
          if (startTs != 0L && endTs != 0L) {
            wr = Annotation.create(startTs, Constants.WIRE_RECV, local);
            mr = Annotation.create(endTs, Constants.MESSAGE_RECV, local);
          } else if (startTs != 0L) {
            mr = Annotation.create(startTs, Constants.MESSAGE_RECV, local);
          }
          break;
        default:
          throw new AssertionError("update kind mapping");
      }
    }

    for (Map.Entry<String, String> tag : in.tags().entrySet()) {
      wroteEndpoint = true;
      result.addBinaryAnnotation(BinaryAnnotation.create(tag.getKey(), tag.getValue(), local));
    }

    if (cs != null
      || sr != null
      || ss != null
      || cr != null
      || ws != null
      || wr != null
      || ms != null
      || mr != null) {
      if (cs != null) result.addAnnotation(cs);
      if (sr != null) result.addAnnotation(sr);
      if (ss != null) result.addAnnotation(ss);
      if (cr != null) result.addAnnotation(cr);
      if (ws != null) result.addAnnotation(ws);
      if (wr != null) result.addAnnotation(wr);
      if (ms != null) result.addAnnotation(ms);
      if (mr != null) result.addAnnotation(mr);
      wroteEndpoint = true;
    } else if (local != null && remote != null) {
      // special-case when we are missing core annotations, but we have both address annotations
      result.addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, local));
      wroteEndpoint = true;
      remoteEndpointType = SERVER_ADDR;
    }

    if (remoteEndpointType != null && remote != null) {
      result.addBinaryAnnotation(BinaryAnnotation.address(remoteEndpointType, remote));
    }

    // don't report server-side timestamp on shared or incomplete spans
    if (Boolean.TRUE.equals(in.shared()) && sr != null) {
      result.timestamp(null).duration(null);
    }
    if (local != null && !wroteEndpoint) { // create a dummy annotation
      result.addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "", local));
    }
    return result.build();
  }

  public static zipkin.internal.v2.Endpoint convert(zipkin.Endpoint input) {
    zipkin.internal.v2.Endpoint.Builder result = zipkin.internal.v2.Endpoint.newBuilder()
      .serviceName(input.serviceName)
      .port(input.port != null ? input.port & 0xffff : null);
    if (input.ipv4 != 0) {
      result.parseIp(new StringBuilder()
        .append(input.ipv4 >> 24 & 0xff).append('.')
        .append(input.ipv4 >> 16 & 0xff).append('.')
        .append(input.ipv4 >> 8 & 0xff).append('.')
        .append(input.ipv4 & 0xff).toString());
    }
    if (input.ipv6 != null) {
      try {
        result.parseIp(Inet6Address.getByAddress(input.ipv6));
      } catch (UnknownHostException e) {
        throw new AssertionError(e); // ipv6 is fixed length, so shouldn't happen.
      }
    }
    return result.build();
  }

  public static zipkin.Endpoint convert(Endpoint input) {
    zipkin.Endpoint.Builder result = zipkin.Endpoint.builder()
      .serviceName(input.serviceName() != null ? input.serviceName() : "")
      .port(input.port() != null ? input.port() : 0);
    if (input.ipv6() != null) {
      result.parseIp(input.ipv6()); // parse first in case there's a mapped IP
    }
    if (input.ipv4() != null) {
      result.parseIp(input.ipv4());
    }
    return result.build();
  }
}
