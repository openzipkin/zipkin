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
package zipkin2.v1;

import java.util.Map;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

/**
 * Allows you convert a v2 span into a v1 span. This is helpful for legacy storage which still use
 * annotations. This shouldn't be used by new code.
 *
 * <p>This type isn't thread-safe: it re-uses state to avoid re-allocations in conversion loops.
 */
public final class V2SpanConverter {

  public static V2SpanConverter create() {
    return new V2SpanConverter();
  }

  final V1Span.Builder result = V1Span.newBuilder();
  final V1SpanMetadata md = new V1SpanMetadata();

  public V1Span convert(Span value) {
    md.parse(value);
    result
        .clear()
        .traceId(value.traceId())
        .parentId(value.parentId())
        .id(value.id())
        .name(value.name())
        .debug(value.debug());

    // Don't report timestamp and duration on shared spans (should be server, but not necessarily)
    if (!Boolean.TRUE.equals(value.shared())) {
      result.timestamp(value.timestampAsLong());
      result.duration(value.durationAsLong());
    }

    boolean beginAnnotation = md.startTs != 0L && md.begin != null;
    boolean endAnnotation = md.endTs != 0L && md.end != null;
    Endpoint ep = value.localEndpoint();
    int annotationCount = value.annotations().size();
    if (beginAnnotation) {
      annotationCount++;
      result.addAnnotation(md.startTs, md.begin, ep);
    }
    for (int i = 0, length = value.annotations().size(); i < length; i++) {
      Annotation a = value.annotations().get(i);
      if (beginAnnotation && a.value().equals(md.begin)) continue;
      if (endAnnotation && a.value().equals(md.end)) continue;
      result.addAnnotation(a.timestamp(), a.value(), ep);
    }
    if (endAnnotation) {
      annotationCount++;
      result.addAnnotation(md.endTs, md.end, ep);
    }

    for (Map.Entry<String, String> b : value.tags().entrySet()) {
      result.addBinaryAnnotation(b.getKey(), b.getValue(), ep);
    }

    boolean writeLocalComponent = annotationCount == 0 && ep != null && value.tags().isEmpty();
    boolean hasRemoteEndpoint = md.addr != null && value.remoteEndpoint() != null;

    // write an empty "lc" annotation to avoid missing the localEndpoint in an in-process span
    if (writeLocalComponent) result.addBinaryAnnotation("lc", "", ep);
    if (hasRemoteEndpoint) result.addBinaryAnnotation(md.addr, value.remoteEndpoint());
    return result.build();
  }

  static final class V1SpanMetadata {
    long startTs, endTs, msTs, wsTs, wrTs, mrTs;
    String begin, end, addr;

    void parse(Span in) {
      startTs = endTs = msTs = wsTs = wrTs = mrTs = 0L;
      begin = end = addr = null;

      startTs = in.timestampAsLong();
      endTs = startTs != 0L && in.durationAsLong() != 0L ? startTs + in.durationAsLong() : 0L;

      Span.Kind kind = in.kind();

      // scan annotations in case there are better timestamps, or inferred kind
      for (int i = 0, length = in.annotations().size(); i < length; i++) {
        Annotation a = in.annotations().get(i);
        String value = a.value();
        if (value.length() != 2) continue;

        if (value.equals("cs")) {
          kind = Span.Kind.CLIENT;
          if (a.timestamp() < startTs) startTs = a.timestamp();
        } else if (value.equals("sr")) {
          kind = Span.Kind.SERVER;
          if (a.timestamp() < startTs) startTs = a.timestamp();
        } else if (value.equals("ss")) {
          kind = Span.Kind.SERVER;
          if (a.timestamp() > endTs) endTs = a.timestamp();
        } else if (value.equals("cr")) {
          kind = Span.Kind.CLIENT;
          if (a.timestamp() > endTs) endTs = a.timestamp();
        } else if (value.equals("ms")) {
          kind = Span.Kind.PRODUCER;
          msTs = a.timestamp();
        } else if (value.equals("mr")) {
          kind = Span.Kind.CONSUMER;
          mrTs = a.timestamp();
        } else if (value.equals("ws")) {
          wsTs = a.timestamp();
        } else if (value.equals("wr")) {
          wrTs = a.timestamp();
        }
      }

      if (in.remoteEndpoint() != null) addr = "sa"; // default value

      if (kind == null) return;

      switch (kind) {
        case CLIENT:
          addr = "sa";
          begin = "cs";
          end = "cr";
          break;
        case SERVER:
          addr = "ca";
          begin = "sr";
          end = "ss";
          break;
        case PRODUCER:
          addr = "ma";
          begin = "ms";
          end = "ws";
          if (startTs == 0L || (msTs != 0 && msTs < startTs)) {
            startTs = msTs;
          }
          if (endTs == 0L || (wsTs != 0 && wsTs > endTs)) {
            endTs = wsTs;
          }
          break;
        case CONSUMER:
          addr = "ma";
          if (startTs == 0L || (wrTs != 0 && wrTs < startTs)) {
            startTs = wrTs;
          }
          if (endTs == 0L || (mrTs != 0 && mrTs > endTs)) {
            endTs = mrTs;
          }
          if (endTs != 0L || wrTs != 0) {
            begin = "wr";
            end = "mr";
          } else {
            begin = "mr";
          }
          break;
        default:
          throw new AssertionError("update kind mapping");
      }

      // If we didn't find a span kind, directly or indirectly, unset the addr
      if (in.remoteEndpoint() == null) addr = null;
    }
  }

  V2SpanConverter() {}
}
