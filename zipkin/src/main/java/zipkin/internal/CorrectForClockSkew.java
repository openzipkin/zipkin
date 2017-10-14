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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin2.internal.Node;

import static java.lang.String.format;
import static java.util.logging.Level.FINE;
import static zipkin.internal.Util.toLowerHex;

/**
 * Adjusts spans whose children happen before their parents, based on core annotation values.
 */
public final class CorrectForClockSkew {
  private static final Logger logger = Logger.getLogger(CorrectForClockSkew.class.getName());

  static class ClockSkew {
    final Endpoint endpoint;
    final long skew;

    public ClockSkew(Endpoint endpoint, long skew) {
      this.endpoint = endpoint;
      this.skew = skew;
    }
  }

  public static List<Span> apply(List<Span> spans) {
    return apply(logger, spans);
  }

  static List<Span> apply(Logger logger, List<Span> spans) {
    if (spans.isEmpty()) return spans;

    String traceId = spans.get(0).traceIdString();
    Long rootSpanId = null;
    Node.TreeBuilder<Span> treeBuilder = new Node.TreeBuilder<>(logger, traceId);

    boolean dataError = false;
    for (int i = 0, length = spans.size(); i < length; i++) {
      Span next = spans.get(i);
      if (next.parentId == null) {
        if (rootSpanId != null) {
          if (logger.isLoggable(FINE)) {
            logger.fine(format("skipping redundant root span: traceId=%s, rootSpanId=%s, spanId=%s",
              traceId, toLowerHex(rootSpanId), toLowerHex(next.id)));
          }
          dataError = true;
          continue;
        }
        rootSpanId = next.id;
      }
      if (!treeBuilder.addNode(
        next.parentId != null ? toLowerHex(next.parentId) : null,
        toLowerHex(next.id),
        next
      )) {
        dataError = true;
      }
    }

    if (rootSpanId == null) {
      if (logger.isLoggable(FINE)) {
        logger.fine("skipping clock skew adjustment due to missing root span: traceId=" + traceId);
      }
      return spans;
    } else if (dataError) {
      if (logger.isLoggable(FINE)) {
        logger.fine("skipping clock skew adjustment due to data errors: traceId=" + traceId);
      }
      return spans;
    }

    Node<Span> tree = treeBuilder.build();
    adjust(tree, null);
    List<Span> result = new ArrayList<>(spans.size());
    for (Iterator<Node<Span>> i = tree.traverse(); i.hasNext(); ) {
      result.add(i.next().value());
    }
    return result;
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
    } else {
      if (skewFromParent != null && isLocalSpan(node.value())) {
        //Propagate skewFromParent to local spans
        skew = skewFromParent;
      }
    }
    // propagate skew to any children
    for (Node<Span> child : node.children()) {
      adjust(child, skew);
    }
  }

  static boolean isLocalSpan(Span span) {
    Endpoint endPoint = null;
    for (int i = 0, length = span.annotations.size(); i < length; i++) {
      Annotation annotation = span.annotations.get(i);
      if (endPoint == null) {
        endPoint = annotation.endpoint;
      }
      if (endPoint != null && !endPoint.equals(annotation.endpoint)) {
        return false;
      }
    }
    for (int i = 0, length = span.binaryAnnotations.size(); i < length; i++) {
      BinaryAnnotation binaryAnnotation = span.binaryAnnotations.get(i);
      if (endPoint == null) {
        endPoint = binaryAnnotation.endpoint;
      }
      if (endPoint != null && !endPoint.equals(binaryAnnotation.endpoint)) {
        return false;
      }
    }
    return true;
  }

  /** If any annotation has an IP with skew associated, adjust accordingly. */
  static Span adjustTimestamps(Span span, ClockSkew skew) {
    List<Annotation> annotations = null;
    Long annotationTimestamp = null;
    for (int i = 0, length = span.annotations.size(); i < length; i++) {
      Annotation a = span.annotations.get(i);
      if (a.endpoint == null) continue;
      if (ipsMatch(skew.endpoint, a.endpoint)) {
        if (annotations == null) annotations = new ArrayList<>(span.annotations);
        if (span.timestamp != null && a.timestamp == span.timestamp) {
          annotationTimestamp = a.timestamp;
        }
        annotations.set(i, a.toBuilder().timestamp(a.timestamp - skew.skew).build());
      }
    }
    if (annotations != null) {
      Span.Builder builder = span.toBuilder().annotations(annotations);
      if (annotationTimestamp != null) {
        builder.timestamp(annotationTimestamp - skew.skew);
      }
      return builder.build();
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
    if (skew.ipv6 != null && that.ipv6 != null) {
      if (Arrays.equals(skew.ipv6, that.ipv6)) return true;
    }
    if (skew.ipv4 != 0 && that.ipv4 != 0 ) {
      if (skew.ipv4 == that.ipv4) return true;
    }
    return false;
  }

  /** Use client/server annotations to determine if there's clock skew. */
  @Nullable
  static ClockSkew getClockSkew(Span span) {
    Map<String, Annotation> annotations = asMap(span.annotations);

    Annotation clientSend = annotations.get(Constants.CLIENT_SEND);
    Annotation clientRecv = annotations.get(Constants.CLIENT_RECV);
    Annotation serverRecv = annotations.get(Constants.SERVER_RECV);
    Annotation serverSend = annotations.get(Constants.SERVER_SEND);

    if (clientSend == null || clientRecv == null || serverRecv == null || serverSend == null) {
      return null;
    }

    Endpoint server = serverRecv.endpoint != null ? serverRecv.endpoint : serverSend.endpoint;
    if (server == null) return null;
    Endpoint client = clientSend.endpoint != null ? clientSend.endpoint : clientRecv.endpoint;
    if (client == null) return null;

    // There's no skew if the RPC is going to itself
    if (ipsMatch(server, client)) return null;

    long clientDuration = clientRecv.timestamp - clientSend.timestamp;
    long serverDuration = serverSend.timestamp - serverRecv.timestamp;
    // We assume latency is half the difference between the client and server duration.
    // This breaks if client duration is smaller than server (due to async return for example).
    if (clientDuration < serverDuration) return null;

    long latency = (clientDuration - serverDuration) / 2;
    // We can't see skew when send happens before receive
    if (latency < 0) return null;

    long skew = serverRecv.timestamp - latency - clientSend.timestamp;
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

  private CorrectForClockSkew() {
  }
}
