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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import zipkin.DependencyLink;
import zipkin.Span;

import static java.util.logging.Level.FINE;

/**
 * This parses a span tree into dependency links used by Web UI. Ex. http://zipkin/dependency
 *
 * <p>This implementation traverses the tree, and only creates links between {@link
 * DependencyLinkSpan.Kind#SERVER server} spans. One exception is at the bottom of the trace tree.
 * {@link DependencyLinkSpan.Kind#CLIENT client} spans that record their {@link
 * DependencyLinkSpan#peerService peer} are included, as this accounts for uninstrumented services.
 */
public final class DependencyLinker {
  private static final Logger logger = Logger.getLogger(DependencyLinker.class.getName());

  private final Map<Pair<String>, Long> linkMap = new LinkedHashMap<>();

  /**
   * @param spans spans where all spans have the same trace id
   */
  public DependencyLinker putTrace(List<Span> spans) {
    if (spans.isEmpty()) return this;

    List<DependencyLinkSpan> linkSpans = new LinkedList<>();
    for (Span s : MergeById.apply(spans)) {
      linkSpans.add(DependencyLinkSpan.from(s));
    }
    return putTrace(linkSpans.iterator());
  }

  /**
   * @param spans spans where all spans have the same trace id
   */
  public DependencyLinker putTrace(Iterator<DependencyLinkSpan> spans) {
    if (!spans.hasNext()) return this;

    Node.TreeBuilder<DependencyLinkSpan> builder = new Node.TreeBuilder<>();
    while (spans.hasNext()) {
      DependencyLinkSpan next = spans.next();
      builder.addNode(next.parentId, next.id, next);
    }
    Node<DependencyLinkSpan> tree = builder.build();

    if (logger.isLoggable(FINE)) logger.fine("traversing trace tree, breadth-first");
    for (Iterator<Node<DependencyLinkSpan>> i = tree.traverse(); i.hasNext(); ) {
      Node<DependencyLinkSpan> current = i.next();
      if (logger.isLoggable(FINE)) {
        logger.fine("processing " + current.value());
      }
      String child;
      String parent;
      switch (current.value().kind) {
        case SERVER:
          child = current.value().service;
          parent = current.value().peerService;
          if (current == tree) { // we are the root-most span.
            if (parent == null) {
              logger.fine("root's peer is unknown; skipping");
              continue;
            }
          }
          break;
        case CLIENT:
          child = current.value().peerService;
          parent = current.value().service;
          break;
        default:
          logger.fine("non-rpc span; skipping");
          continue;
      }

      if (logger.isLoggable(FINE) && parent == null) {
        logger.fine("cannot determine parent, looking for first server ancestor");
      }

      // Local spans may be between the current node and its remote ancestor
      // Look up the stack until we see a service name, and assume that's the client
      Node<DependencyLinkSpan> ancestor = current.parent();
      while (ancestor != null && parent == null) {
        if (logger.isLoggable(FINE)) {
          logger.fine("processing ancestor " + ancestor.value());
        }
        if (ancestor.value().kind == DependencyLinkSpan.Kind.SERVER) {
          parent = ancestor.value().service;
        }
        ancestor = ancestor.parent();
      }

      if (parent == null || child == null) {
        logger.fine("cannot find server ancestor; skipping");
        continue;
      } else if (logger.isLoggable(FINE)) {
        logger.fine("incrementing link " + parent + " -> " + child);
      }

      Pair<String> key = Pair.create(parent, child);
      if (linkMap.containsKey(key)) {
        linkMap.put(key, linkMap.get(key) + 1);
      } else {
        linkMap.put(key, 1L);
      }
    }
    return this;
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
}
