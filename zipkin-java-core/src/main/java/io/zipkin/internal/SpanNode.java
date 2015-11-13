/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.internal;

import io.zipkin.Span;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static io.zipkin.internal.Util.checkNotNull;

final class SpanNode {
  /** mutable to avoid allocating lists for no reason */
  Span span;
  List<SpanNode> children = Collections.emptyList();

  private SpanNode(Span span) {
    this.span = checkNotNull(span, "span");
  }

  void addChild(SpanNode node) {
    if (children.equals(Collections.emptyList())) children = new LinkedList<>();
    children.add(node);
  }

  static SpanNode create(Span span, List<Span> spans) {
    SpanNode rootNode = new SpanNode(span);

    // Initialize nodes representing the trace tree
    Map<Long, SpanNode> idToNode = new LinkedHashMap<>();
    for (Span s : spans) {
      if (s.parentId == null) continue; // special-case root
      idToNode.put(s.id, new SpanNode(s));
    }

    // Collect the parent-child relationships between all spans.
    Map<Long, Long> idToParent = new LinkedHashMap<>();
    for (Map.Entry<Long, SpanNode> entry : idToNode.entrySet()) {
      idToParent.put(entry.getKey(), entry.getValue().span.parentId);
    }

    // Materialize the tree using parent - child relationships
    for (Map.Entry<Long, Long> entry : idToParent.entrySet()) {
      SpanNode node = idToNode.get(entry.getKey());
      SpanNode parent = idToNode.get(entry.getValue());
      if (parent == null) { // attribute missing parents to root
        rootNode.addChild(node);
      } else {
        parent.addChild(node);
      }
    }
    return rootNode;
  }

  List<Span> toSpans() {
    if (children.isEmpty()) {
      return Collections.singletonList(span);
    }
    List<Span> result = new LinkedList<>();
    result.add(span);
    for (SpanNode child : children) {
      result.addAll(child.toSpans());
    }
    return result;
  }
}
