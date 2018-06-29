/*
 * Convenience type representing a tree. This is here because multiple facets in zipkin require
 * traversing the trace tree. For example, looking at network boundaries to correct clock skew, or
 * counting requests imply visiting the tree.
 */
// originally zipkin2.internal.Node.java
class Node {
  constructor(value) {
    this._parent = undefined; // no default
    this._value = value; // undefined is possible when this is a synthetic root node
    this._children = [];
    this._missingRootDummyNode = false;
  }

  // Returns the parent, or undefined if root.
  get parent() {
    return this._parent;
  }

  // Returns the value, or undefined if a synthetic root node
  get value() {
    return this._value;
  }

  // Returns the children of this node
  get children() {
    return this._children;
  }

  // Mutable as some transformations, such as clock skew, adjust the current node in the tree.
  setValue(newValue) {
    if (!newValue) throw new Error('newValue was undefined');
    this._value = newValue;
  }

  _setParent(newParent) {
    this._parent = newParent;
  }

  addChild(child) {
    if (child === this) throw new Error(`circular dependency on ${this.toString()}`);
    child._setParent(this);
    this._children.push(child);
  }

  // Returns an array of values resulting from a breadth-first traversal at this node
  traverse() {
    const result = [];
    const queue = [this];

    while (queue.length > 0) {
      const current = queue.shift();

      // when there's a synthetic root span, the value could be undefined
      if (current.value) result.push(current.value);

      const children = current.children;
      for (let i = 0; i < children.length; i++) {
        queue.push(children[i]);
      }
    }
    return result;
  }

  toString() {
    if (this._value) return `Node(${JSON.stringify(this._value)})`;
    return 'Node()';
  }
}

/*
 * Some operations do not require the entire span object. This creates a tree given (parent id,
 * id) pairs.
 */
class TreeBuilder {
  constructor(params) {
    const {traceId, debug = false} = params;
    this._mergeFunction = (existing, update) => existing || update; // first non null
    if (!traceId) throw new Error('traceId was undefined');
    this._traceId = traceId;
    this._debug = debug;
    this._rootId = undefined;
    this._rootNode = undefined;
    this._entries = [];
      // Nodes representing the trace tree
    this._idToNode = {};
      // Collect the parent-child relationships between all spans.
    this._idToParent = {};
  }

  // Returns false after logging on debug if the value couldn't be added
  addNode(parentId, id, value) {
    if (parentId && parentId === id) {
      if (this._debug) {
        /* eslint-disable no-console */
        console.log(`skipping circular dependency: traceId=${this._traceId}, spanId=${id}`);
      }
      return false;
    }
    this._idToParent[id] = parentId;
    this._entries.push({parentId, id, value});
    return true;
  }

  _processNode(entry) {
    let parentId = entry.parentId ? entry.parentId : this._idToParent[entry.id];
    const id = entry.id;
    const value = entry.value;

    if (!parentId) {
      if (this._rootId) {
        if (this._debug) {
          const prefix = 'attributing span missing parent to root';
          /* eslint-disable no-console */
          console.log(
            `${prefix}: traceId=${this._traceId}, rootSpanId=${this._rootId}, spanId=${id}`
          );
        }
        parentId = this._rootId;
        this._idToParent[id] = parentId;
      } else {
        this._rootId = id;
      }
    }

    const node = new Node(value);
    // special-case root, and attribute missing parents to it. In
    // other words, assume that the first root is the "real" root.
    if (!parentId && !this._rootNode) {
      this._rootNode = node;
      this._rootId = id;
    } else if (!parentId && this._rootId === id) {
      this._rootNode.setValue(this._mergeFunction(this._rootNode.value, node.value));
    } else {
      const previous = this._idToNode[id];
      this._idToNode[id] = node;
      if (previous) node.setValue(this._mergeFunction(previous.value, node.value));
    }
  }

  // Builds a tree from calls to addNode, or returns an empty tree.
  build() {
    this._entries.forEach((n) => this._processNode(n));
    if (!this._rootNode) {
      if (this._debug) {
        /* eslint-disable no-console */
        console.log(`substituting dummy node for missing root span: traceId=${this._traceId}`);
      }
      this._rootNode = new Node();
    }

    // Materialize the tree using parent - child relationships
    Object.keys(this._idToParent).forEach(id => {
      if (id === this._rootId) return; // don't re-process root

      const node = this._idToNode[id];
      const parent = this._idToNode[this._idToParent[id]];
      if (!parent) { // handle headless
        this._rootNode.addChild(node);
      } else {
        parent.addChild(node);
      }
    });
    return this._rootNode;
  }
}

module.exports = {
  Node,
  TreeBuilder
};
