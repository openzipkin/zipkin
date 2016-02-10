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
import java.util.List;
import java.util.Map;
import zipkin.DependencyLink;

/**
 * This parses a span tree into dependency links used by Web UI. Ex. http://zipkin/dependency
 *
 * <p>This implementation traverses the tree, and only creates links between {@link
 * DependencyLinkSpan.Kind#SERVER server} spans. One exception is at the bottom of the trace tree.
 * {@link DependencyLinkSpan.Kind#CLIENT client} spans that record their {@link
 * DependencyLinkSpan#peerService peer} are included, as this accounts for uninstrumented
 * services.
 */
public final class DependencyLinker {

  private final Map<Pair<String>, Long> linkMap = new LinkedHashMap<>();

  /**
   * @param spans spans where all spans have the same trace id
   */
  public void putTrace(Iterator<DependencyLinkSpan> spans) {
    if (!spans.hasNext()) return;

    Node.TreeBuilder<DependencyLinkSpan> builder = new Node.TreeBuilder<>();
    while (spans.hasNext()) {
      DependencyLinkSpan next = spans.next();
      builder.addNode(next.parentId, next.spanId, next);
    }
    Node<DependencyLinkSpan> tree = builder.build();

    // find any nodes who have
    for (Iterator<Node<DependencyLinkSpan>> i = tree.traverse(); i.hasNext(); ) {
      Node<DependencyLinkSpan> current = i.next();
      String server;
      String client;
      switch (current.value().kind) {
        case SERVER:
          server = current.value().service;
          client = current.value().peerService;
          if (current == tree) { // we are the root-most span.
            if (client == null) {
              continue; // skip if we can't read the root's uninstrumented client
            }
          }
          break;
        case CLIENT:
          server = current.value().peerService;
          client = current.value().service;
          break;
        default:
          continue; // skip if we are missing the server's name
      }

      // Local spans may be between the current node and its remote ancestor
      // Look up the stack until we see a service name, and assume that's the client
      Node<DependencyLinkSpan> parent = current.parent();
      while (parent != null && client == null) {
        if (parent.value().kind == DependencyLinkSpan.Kind.SERVER) {
          client = parent.value().service;
        }
        parent = parent.parent();
      }
      if (client == null) continue; // skip if no ancestors were servers

      Pair<String> key = Pair.create(client, server);
      if (linkMap.containsKey(key)) {
        linkMap.put(key, linkMap.get(key) + 1);
      } else {
        linkMap.put(key, 1L);
      }
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
}
