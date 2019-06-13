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

    // Let's cleanup any spans and pick the longest ID
    String traceId = result.get(0).traceId();
    for (int i = 1; i < length; i++) {
      String nextTraceId = result.get(i).traceId();
      if (traceId.length() != 32) traceId = nextTraceId;
    }

    // Now start any fixes or merging
    Span last = null;
    for (int i = 0; i < length; i++) {
      Span span = result.get(i);
      //String previousId = last.id();
      boolean spanShared = Boolean.TRUE.equals(span.shared());

      // Choose the longest trace ID
      Span.Builder replacement = null;
      if (span.traceId().length() != traceId.length()) {
        replacement = span.toBuilder().traceId(traceId);
      }

      EndpointTracker localEndpoint = null;
      while (i + 1 < length) {
        Span next = result.get(i + 1);
        String nextId = next.id();
        if (!nextId.equals(span.id())) break;

        if (localEndpoint == null) {
          localEndpoint = new EndpointTracker();
          localEndpoint.tryMerge(span.localEndpoint());
        }

        // This cautiously merges with the next span, if we think it was sent in multiple pieces.
        boolean nextShared = Boolean.TRUE.equals(next.shared());
        if (spanShared == nextShared && localEndpoint.tryMerge(next.localEndpoint())) {
          if (replacement == null) replacement = span.toBuilder();
          replacement.merge(next);

          // remove the merged element
          length--;
          result.remove(i + 1);
          continue;
        }
        break;
      }

      // Zipkin and B3 originally used the same span ID between client and server. Some
      // instrumentation are inconsistent about adding the shared flag on the server side. Since we
      // have the entire trace, and it is ordered client-first, we can correct a missing shared flag.
      if (last != null && last.id().equals(span.id())) {
        // Backfill missing shared flag as some instrumentation doesn't add it
        if (last.kind() == Span.Kind.CLIENT && span.kind() == Span.Kind.SERVER && !spanShared) {
          spanShared = true;
          if (replacement == null) replacement = span.toBuilder();
          replacement.shared(true);
        }

        if (spanShared && span.parentId() == null && last.parentId() != null) {
          // handle a shared RPC server span that wasn't propagated its parent span ID
          if (replacement == null) replacement = span.toBuilder();
          replacement.parentId(last.parentId());
        }
      }

      if (replacement != null) {
        span = replacement.build();
        result.set(i, span);
      }
      last = span;
    }

    return result;
  }

  static final Comparator<Span> CLEANUP_COMPARATOR = new Comparator<Span>() {
    @Override public int compare(Span left, Span right) {
      if (left.equals(right)) return 0;
      int bySpanId = left.id().compareTo(right.id());
      if (bySpanId != 0) return bySpanId;
      int byShared = compareShared(left, right);
      if (byShared != 0) return byShared;
      return compareEndpoint(left.localEndpoint(), right.localEndpoint());
    }
  };

  // false or null first (client first)
  static int compareShared(Span left, Span right) {
    // If either are shared put it last
    boolean leftShared = Boolean.TRUE.equals(left.shared());
    boolean rightShared = Boolean.TRUE.equals(right.shared());
    if (leftShared && rightShared) return 0; // both are shared, so skip out
    if (leftShared) return 1;
    if (rightShared) return -1;

    // neither are shared, put the client spans first
    boolean leftClient = Span.Kind.CLIENT.equals(left.kind());
    boolean rightClient = Span.Kind.CLIENT.equals(right.kind());
    if (leftClient && rightClient) return 0;
    if (leftClient) return -1;
    if (rightClient) return 1;
    return 0; // neither are client spans
  }

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
