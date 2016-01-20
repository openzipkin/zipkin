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
package zipkin.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zipkin.Annotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;

/**
 * Adjusts spans whose children happen before their parents, based on core annotation values.
 */
public final class CorrectForClockSkew {

  static class ClockSkew {
    final Endpoint endpoint;
    final long skew;

    public ClockSkew(Endpoint endpoint, long skew) {
      this.endpoint = endpoint;
      this.skew = skew;
    }
  }

  public static List<Span> apply(List<Span> spans) {
    for (Span s : spans) {
      if (s.parentId == null) {
        SpanNode tree = SpanNode.create(s, spans);
        adjust(tree, null);
        return tree.toSpans();
      }
    }
    return spans;
  }

  /**
   * Recursively adjust the timestamps on the span tree. Root span is the reference point, all
   * children's timestamps gets adjusted based on that span's timestamps.
   */
  private static void adjust(SpanNode node, @Nullable ClockSkew skewFromParent) {
    // adjust skew for the endpoint brought over from the parent span
    if (skewFromParent != null) {
      node.span = adjustTimestamps(node.span, skewFromParent);
    }

    // Is there any skew in the current span?
    ClockSkew skew = getClockSkew(node.span);
    if (skew != null) {
      // the current span's skew may be a different endpoint than skewFromParent, adjust again.
      node.span = adjustTimestamps(node.span, skew);

      // propagate skew to any children
      for (SpanNode child : node.children) {
        adjust(child, skew);
      }
    }
  }

  /** If any annotation has an IP with skew associated, adjust accordingly. */
  private static Span adjustTimestamps(Span span, ClockSkew clockSkew) {
    Annotation[] annotations = null;
    int length = span.annotations.size();
    for (int i = 0; i < length; i++) {
      Annotation a = span.annotations.get(i);
      if (a.endpoint == null) continue;
      if (clockSkew.endpoint.ipv4 == a.endpoint.ipv4) {
        if (annotations == null) annotations = span.annotations.toArray(new Annotation[length]);
        annotations[i] = new Annotation.Builder(a).timestamp(a.timestamp - clockSkew.skew).build();
      }
    }
    if (annotations == null) return span;
    // reset timestamp and duration as if there's skew, these will change.
    long first = annotations[0].timestamp;
    long last = annotations[length - 1].timestamp;
    long duration = last - first;
    return new Span.Builder(span).timestamp(first).duration(duration).annotations(annotations).build();
  }

  /** Use client/server annotations to determine if there's clock skew. */
  @Nullable
  private static ClockSkew getClockSkew(Span span) {
    Map<String, Annotation> annotations = asMap(span.annotations);

    Long clientSend = getTimestamp(annotations, Constants.CLIENT_SEND);
    Long clientRecv = getTimestamp(annotations, Constants.CLIENT_RECV);
    Long serverRecv = getTimestamp(annotations, Constants.SERVER_RECV);
    Long serverSend = getTimestamp(annotations, Constants.SERVER_SEND);

    if (clientSend == null || clientRecv == null || serverRecv == null || serverSend == null) {
      return null;
    }

    Endpoint server = annotations.get(Constants.SERVER_RECV).endpoint;
    server = server == null ? annotations.get(Constants.SERVER_SEND).endpoint : server;
    if (server == null) return null;

    long clientDuration = clientRecv - clientSend;
    long serverDuration = serverSend - serverRecv;

    // There is only clock skew if CS is after SR or CR is before SS
    boolean csAhead = clientSend < serverRecv;
    boolean crAhead = clientRecv > serverSend;
    if (serverDuration > clientDuration || (csAhead && crAhead)) {
      return null;
    }
    long latency = (clientDuration - serverDuration) / 2;
    long skew = serverRecv - latency - clientSend;
    if (skew != 0L) {
      return new ClockSkew(server, skew);
    }
    return null;
  }

  /** Get the annotations as a map with value to annotation bindings. */
  private static Map<String, Annotation> asMap(List<Annotation> annotations) {
    Map<String, Annotation> result = new LinkedHashMap<>(annotations.size());
    for (Annotation a : annotations) {
      result.put(a.value, a);
    }
    return result;
  }

  @Nullable
  private static Long getTimestamp(Map<String, Annotation> annotations, String value) {
    Annotation result = annotations.get(value);
    return result != null ? result.timestamp : null;
  }

  private CorrectForClockSkew() {
  }
}
