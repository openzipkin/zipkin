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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import zipkin.Span;

/**
 * Convenience type representing a tree. This is here because multiple facets in zipkin require
 * traversing the trace tree. For example, looking at network boundaries to correct clock skew, or
 * counting requests imply visiting the tree.
 *
 * @param <V> the node's value. Ex a full span or a tuple like {@code (serviceName, isLocal)}
 */
public final class Node<V> {

  /** Set via {@link #addChild(Node)} */
  private Node<V> parent;
  /** mutable as some transformations, such as clock skew, adjust this. */
  private V value;
  /** mutable to avoid allocating lists for childless nodes */
  private List<Node<V>> children = Collections.emptyList();
  private boolean missingRootDummyNode;

  /** Returns the parent, or null if root */
  @Nullable
  public Node<V> parent() {
    return parent;
  }

  public V value() {
    return value;
  }

  public Node<V> value(V newValue) {
    this.value = newValue;
    return this;
  }

  public Node<V> addChild(Node<V> child) {
    child.parent = this;
    if (children.equals(Collections.emptyList())) children = new LinkedList<>();
    children.add(child);
    return this;
  }

  /** Returns the children of this node. */
  public Collection<Node<V>> children() {
    return children;
  }

  /** Traverses the tree, breadth-first. */
  public Iterator<Node<V>> traverse() {
    return new BreadthFirstIterator<>(this);
  }

  public boolean isSyntheticRootForPartialTree() {
    return missingRootDummyNode;
  }

  static final class BreadthFirstIterator<V> implements Iterator<Node<V>> {
    private final Queue<Node<V>> queue = new ArrayDeque<>();

    BreadthFirstIterator(Node<V> root) {
      queue.add(root);
    }

    @Override
    public boolean hasNext() {
      return !queue.isEmpty();
    }

    @Override
    public Node<V> next() {
      Node<V> result = queue.remove();
      queue.addAll(result.children);
      return result;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove");
    }
  }

  /**
   * @param trace spans that belong to the same {@link Span#traceId trace}, in any order.
   */
  static Node<Span> constructTree(List<Span> trace) {
    TreeBuilder<Span> treeBuilder = new TreeBuilder<>();
    for (Span s : trace) {
      treeBuilder.addNode(s.parentId, s.id, s);
    }
    return treeBuilder.build();
  }

  /**
   * Some operations do not require the entire span object. This creates a tree given (parent id,
   * id) pairs.
   *
   * @param <V> same type as {@link Node#value}
   */
  public static final class TreeBuilder<V> {
    Node<V> rootNode = null;

    // Nodes representing the trace tree
    Map<Long, Node<V>> idToNode = new LinkedHashMap<>();
    // Collect the parent-child relationships between all spans.
    Map<Long, Long> idToParent = new LinkedHashMap<>(idToNode.size());

    public void addNode(Long parentId, long id, @Nullable V value) {
      Node<V> node = new Node<V>().value(value);
      if (parentId == null) {
        // special-case root, and attribute missing parents to it. In
        // other words, assume that the first root is the "real" root.
        if (rootNode == null) {
          rootNode = node;
        } else {
          idToNode.put(id, node);
          idToParent.put(id, null);
        }
      } else {
        idToNode.put(id, node);
        idToParent.put(id, parentId);
      }
    }

    /** Builds a tree from calls to {@link #addNode}, or returns an empty tree. */
    public Node<V> build() {
      // Materialize the tree using parent - child relationships
      for (Map.Entry<Long, Long> entry : idToParent.entrySet()) {
        Node<V> node = idToNode.get(entry.getKey());
        Node<V> parent = idToNode.get(entry.getValue());
        if (parent == null) { // handle headless trace
          if (rootNode == null) {
            rootNode = new Node<>();
            rootNode.missingRootDummyNode = true;
          }
          rootNode.addChild(node);
        } else {
          parent.addChild(node);
        }
      }
      return rootNode != null ? rootNode : new Node<>();
    }
  }
}
