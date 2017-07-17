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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Span2.Kind;

import static java.util.logging.Level.FINE;

/**
 * This parses a span tree into dependency links used by Web UI. Ex. http://zipkin/dependency
 *
 * <p>This implementation traverses the tree, and only creates links between {@link Kind#SERVER
 * server} spans. One exception is at the bottom of the trace tree. {@link Kind#CLIENT client} spans
 * that record their {@link Span2#remoteEndpoint()} are included, as this accounts for
 * uninstrumented services. Spans with {@link Span2#kind()} unset, but {@link
 * Span2#remoteEndpoint()} set are treated the same as client spans.
 */
public final class DependencyLinker {
  private final Logger logger;
  private final Map<Pair<String>, Long> linkMap = new LinkedHashMap<>();

  public DependencyLinker() {
    this(Logger.getLogger(DependencyLinker.class.getName()));
  }

  DependencyLinker(Logger logger) {
    this.logger = logger;
  }

  /**
   * @param spans spans where all spans have the same trace id
   */
  public DependencyLinker putTrace(Collection<Span> spans) {
    if (spans.isEmpty()) return this;

    List<Span2> linkSpans = new LinkedList<>();
    for (Span s : MergeById.apply(spans)) {
      linkSpans.addAll(Span2Converter.fromSpan(s));
    }
    return putTrace(linkSpans.iterator());
  }

  static final Node.MergeFunction<Span2> MERGE_RPC = new Node.MergeFunction<Span2>() {
    @Override public Span2 merge(Span2 existing, Span2 update) {
      if (existing == null) return update;
      if (update == null) return existing;
      if (existing.kind() == null) return update;
      if (update.kind() == null) return existing;
      Span2 server = existing.kind() == Kind.SERVER ? existing : update;
      Span2 client = existing == server ? update : existing;
      if (server.remoteEndpoint() != null && !"".equals(server.remoteEndpoint().serviceName)) {
        return server;
      }
      return server.toBuilder().remoteEndpoint(client.localEndpoint()).build();
    }

    @Override public String toString() {
      return "MergeRpc";
    }
  };

  /**
   * @param spans spans where all spans have the same trace id
   */
  public DependencyLinker putTrace(Iterator<Span2> spans) {
    if (!spans.hasNext()) return this;

    Span2 first = spans.next();
    Node.TreeBuilder<Span2> builder =
      new Node.TreeBuilder<>(logger, MERGE_RPC, first.traceIdString());
    builder.addNode(first.parentId(), first.id(), first);
    while (spans.hasNext()) {
      Span2 next = spans.next();
      builder.addNode(next.parentId(), next.id(), next);
    }
    Node<Span2> tree = builder.build();

    if (logger.isLoggable(FINE)) logger.fine("traversing trace tree, breadth-first");
    for (Iterator<Node<Span2>> i = tree.traverse(); i.hasNext(); ) {
      Node<Span2> current = i.next();
      Span2 currentSpan = current.value();
      if (logger.isLoggable(FINE)) {
        logger.fine("processing " + currentSpan);
      }
      if (current.isSyntheticRootForPartialTree()) {
        logger.fine("skipping synthetic node for broken span tree");
        continue;
      }

      Kind kind = currentSpan.kind();
      if (Kind.CLIENT.equals(kind) && !current.children().isEmpty()) {
        logger.fine("deferring link to rpc child span");
        continue;
      }

      String serviceName = serviceName(currentSpan);
      String remoteServiceName = remoteServiceName(currentSpan);
      if (kind == null) {
        // Treat unknown type of span as a client span if we know both sides
        if (serviceName != null && remoteServiceName != null) {
          kind = Kind.CLIENT;
        } else {
          logger.fine("non-rpc span; skipping");
          continue;
        }
      }

      String child;
      String parent;
      switch (kind) {
        case SERVER:
          child = serviceName;
          parent = remoteServiceName;
          if (current == tree) { // we are the root-most span.
            if (parent == null) {
              logger.fine("root's peer is unknown; skipping");
              continue;
            }
          }
          break;
        case CLIENT:
          parent = serviceName;
          child = remoteServiceName;
          break;
        default:
          logger.fine("unknown kind; skipping");
          continue;
      }

      if (logger.isLoggable(FINE) && parent == null) {
        logger.fine("cannot determine parent, looking for first server ancestor");
      }

      String rpcAncestor = findRpcAncestor(current);
      if (rpcAncestor != null) {

        // Local spans may be between the current node and its remote parent
        if (parent == null) parent = rpcAncestor;

        // Some users accidentally put the remote service name on client annotations.
        // Check for this and backfill a link from the nearest remote to that service as necessary.
        if (Kind.CLIENT.equals(kind) && serviceName != null && !rpcAncestor.equals(serviceName)) {
          logger.fine("detected missing link to client span");
          addLink(rpcAncestor, serviceName);
          continue;
        }
      }

      if (parent == null || child == null) {
        logger.fine("cannot find server ancestor; skipping");
        continue;
      }

      addLink(parent, child);
    }
    return this;
  }

  String findRpcAncestor(Node<Span2> current) {
    Node<Span2> ancestor = current.parent();
    while (ancestor != null) {
      if (logger.isLoggable(FINE)) {
        logger.fine("processing ancestor " + ancestor.value());
      }
      if (!ancestor.isSyntheticRootForPartialTree()) {
        Span2 maybeRemote = ancestor.value();
        if (maybeRemote.kind() != null) {
          return serviceName(maybeRemote);
        }
      }
      ancestor = ancestor.parent();
    }
    return null;
  }

  void addLink(String parent, String child) {
    if (logger.isLoggable(FINE)) {
      logger.fine("incrementing link " + parent + " -> " + child);
    }
    Pair<String> key = Pair.create(parent, child);
    if (linkMap.containsKey(key)) {
      linkMap.put(key, linkMap.get(key) + 1);
    } else {
      linkMap.put(key, 1L);
    }
  }

  public List<DependencyLink> link() {
    // links are merged by mapping to parent/child and summing corresponding links
    List<DependencyLink> result = new ArrayList<>(linkMap.size());
    for (Map.Entry<Pair<String>, Long> entry : linkMap.entrySet()) {
      result.add(DependencyLink.create(entry.getKey()._1, entry.getKey()._2, entry.getValue()));
    }
    return result;
  }

  /** links are merged by mapping to parent/child and summing corresponding links */
  public static List<DependencyLink> merge(Iterable<DependencyLink> in) {
    Map<Pair<String>, Long> links = new LinkedHashMap<>();

    for (DependencyLink link : in) {
      Pair<String> parentChild = Pair.create(link.parent, link.child);
      long callCount = links.containsKey(parentChild) ? links.get(parentChild) : 0L;
      callCount += link.callCount;
      links.put(parentChild, callCount);
    }

    List<DependencyLink> result = new ArrayList<>(links.size());
    for (Map.Entry<Pair<String>, Long> link : links.entrySet()) {
      result.add(DependencyLink.create(link.getKey()._1, link.getKey()._2, link.getValue()));
    }
    return result;
  }

  static String serviceName(Span2 span) {
    return span.localEndpoint() != null && !"".equals(span.localEndpoint().serviceName)
      ? span.localEndpoint().serviceName
      : null;
  }

  static String remoteServiceName(Span2 span) {
    return span.remoteEndpoint() != null && !"".equals(span.remoteEndpoint().serviceName)
      ? span.remoteEndpoint().serviceName
      : null;
  }
}
