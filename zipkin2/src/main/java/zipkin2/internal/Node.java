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
package zipkin2.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.util.logging.Level.FINE;

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
  @Nullable public Node<V> parent() {
    return parent;
  }

  /** Returns the value, or null if {@link #isSyntheticRootForPartialTree} */
  @Nullable public V value() {
    return value;
  }

  public Node<V> value(V newValue) {
    if (newValue == null) throw new NullPointerException("newValue == null");
    this.value = newValue;
    return this;
  }

  public Node<V> addChild(Node<V> child) {
    if (child == this) throw new IllegalArgumentException("circular dependency on " + this);
    child.parent = this;
    if (children.equals(Collections.emptyList())) children = new ArrayList<>();
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

  interface MergeFunction<V> {
    V merge(@Nullable V existing, @Nullable V update);
  }

  static final MergeFunction FIRST_NOT_NULL = new MergeFunction() {
    @Override public Object merge(@Nullable Object existing, @Nullable Object update) {
      return existing != null ? existing : update;
    }
  };

  /**
   * Some operations do not require the entire span object. This creates a tree given (parent id,
   * id) pairs.
   *
   * @param <V> same type as {@link Node#value}
   */
  public static final class TreeBuilder<V> {
    final Logger logger;
    final MergeFunction<V> mergeFunction;
    final String traceId;

    public TreeBuilder(Logger logger, String traceId) {
      this(logger, FIRST_NOT_NULL, traceId);
    }

    TreeBuilder(Logger logger, MergeFunction<V> mergeFunction, String traceId) {
      this.logger = logger;
      this.mergeFunction = mergeFunction;
      this.traceId = traceId;
    }

    String rootId = null;
    Node<V> rootNode = null;
    // Nodes representing the trace tree
    Map<String, Node<V>> idToNode = new LinkedHashMap<>();
    // Collect the parent-child relationships between all spans.
    Map<String, String> idToParent = new LinkedHashMap<>(idToNode.size());

    /** Returns false after logging to FINE if the value couldn't be added */
    public boolean addNode(@Nullable String parentId, String id, V value) {
      if (parentId == null) {
        if (rootId != null) {
          if (logger.isLoggable(FINE)) {
            logger.fine(format(
              "attributing span missing parent to root: traceId=%s, rootSpanId=%s, spanId=%s",
              traceId, rootId, id));
          }
        } else {
          rootId = id;
        }
      } else if (parentId.equals(id)) {
        if (logger.isLoggable(FINE)) {
          logger.fine(
            format("skipping circular dependency: traceId=%s, spanId=%s", traceId, id));
        }
        return false;
      }

      Node<V> node = new Node<V>().value(value);
      // special-case root, and attribute missing parents to it. In
      // other words, assume that the first root is the "real" root.
      if (parentId == null && rootNode == null) {
        rootNode = node;
        rootId = id;
      } else if (parentId == null && rootId.equals(id)) {
        rootNode.value(mergeFunction.merge(rootNode.value, node.value));
      } else {
        Node<V> previous = idToNode.put(id, node);
        if (previous != null) node.value(mergeFunction.merge(previous.value, node.value));
        idToParent.put(id, parentId);
      }
      return true;
    }

    /** Builds a tree from calls to {@link #addNode}, or returns an empty tree. */
    public Node<V> build() {
      // Materialize the tree using parent - child relationships
      for (Map.Entry<String, String> entry : idToParent.entrySet()) {
        Node<V> node = idToNode.get(entry.getKey());
        Node<V> parent = idToNode.get(entry.getValue());
        if (parent == null) { // handle headless
          if (rootNode == null) {
            if (logger.isLoggable(FINE)) {
              logger.fine("substituting dummy node for missing root span: traceId=" + traceId);
            }
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
