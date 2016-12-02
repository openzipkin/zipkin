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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
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
        Node<Span> tree = Node.constructTree(spans);
        adjust(tree, null);
        List<Span> result = new ArrayList<>(spans.size());
        for (Iterator<Node<Span>> i = tree.traverse(); i.hasNext();) {
          result.add(i.next().value());
        }
        return result;
      }
    }
    return spans;
  }

  /**
   * Recursively adjust the timestamps on the span tree. Root span is the reference point, all
   * children's timestamps gets adjusted based on that span's timestamps.
   */
  static void adjust(Node<Span> node, @Nullable ClockSkew skewFromParent) {
    // adjust skew for the endpoint brought over from the parent span
    if (skewFromParent != null) {
      node.value(adjustTimestamps(node.value(), skewFromParent));
    }

    // Is there any skew in the current span?
    ClockSkew skew = getClockSkew(node.value());
    if (skew != null) {
      // the current span's skew may be a different endpoint than skewFromParent, adjust again.
      node.value(adjustTimestamps(node.value(), skew));

      // propagate skew to any children
      for (Node<Span> child : node.children()) {
        adjust(child, skew);
      }
    }
  }

  /** If any annotation has an IP with skew associated, adjust accordingly. */
  static Span adjustTimestamps(Span span, ClockSkew skew) {
    List<Annotation> annotations = null;
    for (int i = 0, length = span.annotations.size(); i < length; i++) {
      Annotation a = span.annotations.get(i);
      if (a.endpoint == null) continue;
      if (ipsMatch(skew.endpoint, a.endpoint)) {
        if (annotations == null) annotations = new ArrayList<>(span.annotations);
        annotations.set(i, a.toBuilder().timestamp(a.timestamp - skew.skew).build());
      }
    }
    if (annotations != null) {
      return span.toBuilder().timestamp(annotations.get(0).timestamp).annotations(annotations).build();
    }
    // Search for a local span on the skewed endpoint
    for (int i = 0, length = span.binaryAnnotations.size(); i < length; i++) {
      BinaryAnnotation b = span.binaryAnnotations.get(i);
      if (b.endpoint == null) continue;
      if (b.key.equals(Constants.LOCAL_COMPONENT) && ipsMatch(skew.endpoint, b.endpoint)) {
        return span.toBuilder().timestamp(span.timestamp - skew.skew).build();
      }
    }
    return span;
  }

  static boolean ipsMatch(Endpoint skew, Endpoint that) {
    return (skew.ipv6 != null && Arrays.equals(skew.ipv6, that.ipv6))
        || (skew.ipv4 != 0 && skew.ipv4 == that.ipv4);
  }

  /** Use client/server annotations to determine if there's clock skew. */
  @Nullable
  static ClockSkew getClockSkew(Span span) {
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
  static Map<String, Annotation> asMap(List<Annotation> annotations) {
    Map<String, Annotation> result = new LinkedHashMap<>(annotations.size());
    for (Annotation a : annotations) {
      result.put(a.value, a);
    }
    return result;
  }

  @Nullable
  static Long getTimestamp(Map<String, Annotation> annotations, String value) {
    Annotation result = annotations.get(value);
    return result != null ? result.timestamp : null;
  }

  private CorrectForClockSkew() {
  }
}
