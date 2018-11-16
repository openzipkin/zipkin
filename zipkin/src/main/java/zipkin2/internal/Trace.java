/*
 * Copyright 2015-2018 The OpenZipkin Authors
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import zipkin2.Endpoint;
import zipkin2.Span;

public class Trace {
  /*
   * Spans can be sent in multiple parts. Also client and server spans can share the same ID. This
   * merges both scenarios.
   */
  public static List<Span> merge(List<Span> spans) {
    int length = spans.size();
    if (length <= 1) return spans;
    List<Span> result = new ArrayList<>(spans);
    Collections.sort(result, CLEANUP_COMPARATOR);

    // Lets pick the longest trace ID
    String traceId = spans.get(0).traceId();
    for (int i = 1; i < length; i++) {
      String nextTraceId = result.get(i).traceId();
      if (traceId.length() != 32) traceId = nextTraceId;
    }

    // Now start any fixes or merging
    for (int i = 0; i < length; i++) {
      Span previous = result.get(i);
      String previousId = previous.id();
      boolean previousShared = Boolean.TRUE.equals(previous.shared());

      Span.Builder replacement = null;
      if (previous.traceId().length() != traceId.length()) {
        replacement = previous.toBuilder().traceId(traceId);
      }

      EndpointTracker localEndpoint = null;
      while (i + 1 < length) {
        Span next = result.get(i + 1);
        String nextId = next.id();
        if (!nextId.equals(previousId)) break;

        if (localEndpoint == null) {
          localEndpoint = new EndpointTracker();
          localEndpoint.tryMerge(previous.localEndpoint());
        }

        // This cautiously merges with the next span, if we think it was sent in multiple pieces.
        boolean nextShared = Boolean.TRUE.equals(next.shared());
        if (previousShared == nextShared && localEndpoint.tryMerge(next.localEndpoint())) {
          if (replacement == null) replacement = previous.toBuilder();
          replacement.merge(next);

          previous = next;
          // remove the merged element
          length--;
          result.remove(i + 1);
          continue;
        }

        if (nextShared && next.parentId() == null && previous.parentId() != null) {
          // handle a shared RPC server span that wasn't propagated its parent span ID
          result.set(i + 1, next.toBuilder().parentId(previous.parentId()).build());
        }
        break;
      }

      if (replacement != null) result.set(i, replacement.build());
    }

    return result;
  }

  static final Comparator<Span> CLEANUP_COMPARATOR = new Comparator<Span>() {
    @Override public int compare(Span left, Span right) {
      if (left.equals(right)) return 0;
      int bySpanId = left.id().compareTo(right.id());
      if (bySpanId != 0) return bySpanId;
      int byShared = nullSafeCompareTo(left.shared(), right.shared(), true);
      if (byShared != 0) return byShared;
      return compareEndpoint(left.localEndpoint(), right.localEndpoint());
    }
  };

  /**
   * Put spans with null endpoints first, so that their data can be attached to the first span with
   * the same ID and endpoint. It is possible that a server can get the same request on a different
   * port. Not addressing this.
   */
  static int compareEndpoint(Endpoint left, Endpoint right) {
    if (left == null) { // nulls first
      return (right == null) ? 0 : -1;
    } else if (right == null) {
      return 1;
    }
    int byService = nullSafeCompareTo(left.serviceName(), right.serviceName(), false);
    if (byService != 0) return byService;
    int byIpV4 = nullSafeCompareTo(left.ipv4(), right.ipv4(), false);
    if (byIpV4 != 0) return byIpV4;
    return nullSafeCompareTo(left.ipv6(), right.ipv6(), false);
  }

  static <T extends Comparable<T>> int nullSafeCompareTo(T left, T right, boolean nullFirst) {
    if (left == null) {
      return (right == null) ? 0 : (nullFirst ? -1 : 1);
    } else if (right == null) {
      return nullFirst ? 1 : -1;
    } else {
      return left.compareTo(right);
    }
  }

  /**
   * Sometimes endpoints can be sent in pieces. This tracks the whether we should merge with
   * something, or if it has a different identity.
   */
  static final class EndpointTracker {
    String serviceName, ipv4, ipv6;
    int port;

    boolean tryMerge(Endpoint endpoint) {
      if (endpoint == null) return true;
      if (serviceName != null &&
        endpoint.serviceName() != null && !serviceName.equals(endpoint.serviceName())) {
        return false;
      }
      if (ipv4 != null && endpoint.ipv4() != null && !ipv4.equals(endpoint.ipv4())) {
        return false;
      }
      if (ipv6 != null && endpoint.ipv6() != null && !ipv6.equals(endpoint.ipv6())) {
        return false;
      }
      if (port != 0 && endpoint.portAsInt() != 0 && port != endpoint.portAsInt()) {
        return false;
      }
      if (serviceName == null) serviceName = endpoint.serviceName();
      if (ipv4 == null) ipv4 = endpoint.ipv4();
      if (ipv6 == null) ipv6 = endpoint.ipv6();
      if (port == 0) port = endpoint.portAsInt();
      return true;
    }
  }

  Trace() {
  }
}
