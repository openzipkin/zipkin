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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.Span.Kind;

import static java.util.logging.Level.FINE;

/**
 * This parses a span tree into dependency links used by Web UI. Ex. http://zipkin/dependency
 *
 * <p>This implementation traverses the tree, and only creates links between {@link Kind#SERVER
 * server} spans. One exception is at the bottom of the trace tree. {@link Kind#CLIENT client} spans
 * that record their {@link Span#remoteEndpoint()} are included, as this accounts for uninstrumented
 * services. Spans with {@link Span#kind()} unset, but {@link Span#remoteEndpoint()} set are treated
 * the same as client spans.
 */
public final class DependencyLinker {
  private final Logger logger;
  private final Map<Pair, Long> callCounts = new LinkedHashMap<>();
  private final Map<Pair, Long> errorCounts = new LinkedHashMap<>();

  public DependencyLinker() {
    this(Logger.getLogger(DependencyLinker.class.getName()));
  }

  DependencyLinker(Logger logger) {
    this.logger = logger;
  }

  /**
   * @param spans spans where all spans have the same trace id
   */
  public DependencyLinker putTrace(List<Span> spans) {
    if (spans.isEmpty()) return this;
    SpanNode traceTree = new SpanNode.Builder(logger).build(spans);

    if (logger.isLoggable(FINE)) logger.fine("traversing trace tree, breadth-first");
    for (Iterator<SpanNode> i = traceTree.traverse(); i.hasNext(); ) {
      SpanNode current = i.next();
      Span currentSpan = current.span();
      if (currentSpan == null) {
        logger.fine("skipping fake root node for broken span tree");
        continue;
      }
      if (logger.isLoggable(FINE)) {
        logger.fine("processing " + currentSpan);
      }

      Kind kind = currentSpan.kind();
      // When processing links to a client span, we prefer the server's name. If we have no child
      // spans, we proceed to use the name the client chose.
      if (Kind.CLIENT.equals(kind) && !current.children().isEmpty()) {
        continue;
      }

      String serviceName = currentSpan.localServiceName();
      String remoteServiceName = currentSpan.remoteServiceName();
      if (kind == null) {
        // Treat unknown type of span as a client span if we know both sides
        if (serviceName != null && remoteServiceName != null) {
          kind = Kind.CLIENT;
        } else {
          logger.fine("non remote span; skipping");
          continue;
        }
      }

      String child;
      String parent;
      switch (kind) {
        case SERVER:
        case CONSUMER:
          child = serviceName;
          parent = remoteServiceName;
          if (current == traceTree) { // we are the root-most span.
            if (parent == null) {
              logger.fine("root's client is unknown; skipping");
              continue;
            }
          }
          break;
        case CLIENT:
        case PRODUCER:
          parent = serviceName;
          child = remoteServiceName;
          break;
        default:
          logger.fine("unknown kind; skipping");
          continue;
      }

      boolean isError = currentSpan.tags().containsKey("error");
      if (kind == Kind.PRODUCER || kind == Kind.CONSUMER) {
        if (parent == null || child == null) {
          logger.fine("cannot link messaging span to its broker; skipping");
        } else {
          addLink(parent, child, isError);
        }
        continue;
      }

      // Local spans may be between the current node and its remote parent
      Span remoteAncestor = firstRemoteAncestor(current);
      String remoteAncestorName;
      if (remoteAncestor != null && (remoteAncestorName = remoteAncestor.localServiceName()) != null) {
        // Some users accidentally put the remote service name on client annotations.
        // Check for this and backfill a link from the nearest remote to that service as necessary.
        if (kind == Kind.CLIENT && serviceName != null && !remoteAncestorName.equals(serviceName)) {
          logger.fine("detected missing link to client span");
          addLink(remoteAncestorName, serviceName, false); // we don't know if there's an error here
        }

        if (kind == Kind.SERVER || parent == null) parent = remoteAncestorName;

        // When an RPC is split between spans, we skip the child (server side). If our parent is a
        // client, we need to check it for errors.
        if (!isError && Kind.CLIENT.equals(remoteAncestor.kind()) &&
          currentSpan.parentId() != null && currentSpan.parentId().equals(remoteAncestor.id())) {
          isError = remoteAncestor.tags().containsKey("error");
        }
      }

      if (parent == null || child == null) {
        logger.fine("cannot find remote ancestor; skipping");
        continue;
      }

      addLink(parent, child, isError);
    }
    return this;
  }

  Span firstRemoteAncestor(SpanNode current) {
    SpanNode ancestor = current.parent();
    while (ancestor != null) {
      Span maybeRemote = ancestor.span();
      if (maybeRemote != null && maybeRemote.kind() != null) {
        if (logger.isLoggable(FINE)) logger.fine("found remote ancestor " + maybeRemote);
        return maybeRemote;
      }
      ancestor = ancestor.parent();
    }
    return null;
  }

  void addLink(String parent, String child, boolean isError) {
    if (logger.isLoggable(FINE)) {
      logger.fine("incrementing " + (isError ? "error " : "") + "link " + parent + " -> " + child);
    }
    Pair key = new Pair(parent, child);
    if (callCounts.containsKey(key)) {
      callCounts.put(key, callCounts.get(key) + 1);
    } else {
      callCounts.put(key, 1L);
    }
    if (!isError) return;
    if (errorCounts.containsKey(key)) {
      errorCounts.put(key, errorCounts.get(key) + 1);
    } else {
      errorCounts.put(key, 1L);
    }
  }

  public List<DependencyLink> link() {
    return link(callCounts, errorCounts);
  }

  /** links are merged by mapping to parent/child and summing corresponding links */
  public static List<DependencyLink> merge(Iterable<DependencyLink> in) {
    Map<Pair, Long> callCounts = new LinkedHashMap<>();
    Map<Pair, Long> errorCounts = new LinkedHashMap<>();

    for (DependencyLink link : in) {
      Pair parentChild = new Pair(link.parent(), link.child());
      long callCount = callCounts.containsKey(parentChild) ? callCounts.get(parentChild) : 0L;
      callCount += link.callCount();
      callCounts.put(parentChild, callCount);
      long errorCount = errorCounts.containsKey(parentChild) ? errorCounts.get(parentChild) : 0L;
      errorCount += link.errorCount();
      errorCounts.put(parentChild, errorCount);
    }

    return link(callCounts, errorCounts);
  }

  static List<DependencyLink> link(Map<Pair, Long> callCounts,
    Map<Pair, Long> errorCounts) {
    List<DependencyLink> result = new ArrayList<>(callCounts.size());
    for (Map.Entry<Pair, Long> entry : callCounts.entrySet()) {
      Pair parentChild = entry.getKey();
      result.add(DependencyLink.newBuilder()
        .parent(parentChild.left)
        .child(parentChild.right)
        .callCount(entry.getValue())
        .errorCount(errorCounts.containsKey(parentChild) ? errorCounts.get(parentChild) : 0L)
        .build());
    }
    return result;
  }

  static final class Pair {
    final String left, right;

    Pair(String left, String right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof Pair)) return false;
      Pair that = (DependencyLinker.Pair) o;
      return left.equals(that.left) && right.equals(that.right);
    }

    @Override
    public int hashCode() {
      int h$ = 1;
      h$ *= 1000003;
      h$ ^= left.hashCode();
      h$ *= 1000003;
      h$ ^= right.hashCode();
      return h$;
    }
  }
}
