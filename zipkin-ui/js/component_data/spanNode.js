import {mergeV2ById} from './spanCleaner';

/*
 * Convenience type representing a trace tree. Multiple Zipkin features require a trace tree. For
 * example, looking at network boundaries to correct clock skew and aggregating requests paths imply
 * visiting the tree.
 */
// originally zipkin2.internal.SpanNode.java
class SpanNode {
  constructor(span) {
    this._parent = undefined; // no default
    this._span = span; // undefined is possible when this is a synthetic root node
    this._children = [];
  }

  // Returns the parent, or undefined if root.
  get parent() {
    return this._parent;
  }

  _setParent(newParent) {
    this._parent = newParent;
  }

  // Returns the span, or undefined if a synthetic root node
  get span() {
    return this._span;
  }

  // Returns the children of this node
  get children() {
    return this._children;
  }

  // Mutable as some transformations, such as clock skew, adjust the current node in the tree.
  setSpan(span) {
    if (!span) throw new Error('span was undefined');
    this._span = span;
  }

  // Adds the child IFF it isn't already a child.
  addChild(child) {
    if (!child) throw new Error('child was undefined');
    if (child === this) throw new Error(`circular dependency on ${this.toString()}`);
    child._setParent(this);
    this._children.push(child);
  }

  // throws an error if the trace was empty
  queueRootMostSpans() {
    const queue = [];
    // since the input data could be headless, we first push onto the queue the root-most spans
    if (typeof(this.span) === 'undefined') { // synthetic root
      this.children.forEach(child => queue.push(child));
    } else {
      queue.push(this);
    }
    if (queue.length === 0) throw new Error('Trace was empty');
    return queue;
  }

  // Invokes the callback for each span resulting from a breadth-first traversal at this node
  traverse(spanCallback) {
    const queue = this.queueRootMostSpans();

    while (queue.length > 0) {
      const current = queue.shift();

      spanCallback(current.span);

      const children = current.children;
      for (let i = 0; i < children.length; i++) {
        queue.push(children[i]);
      }
    }
  }

  toString() {
    if (this._span) return `SpanNode(${JSON.stringify(this._span)})`;
    return 'SpanNode()';
  }
}

// In javascript, dict keys can't be objects
function keyString(id, shared = false, endpoint) {
  if (!shared) return id;
  const endpointString = endpoint ? JSON.stringify(endpoint) : 'x';
  return `${id}-${endpointString}`;
}

class SpanNodeBuilder {
  constructor(params) {
    const {debug = false} = params;
    this._debug = debug;
    this._rootSpan = undefined;
    this._keyToNode = {};
    this._spanToParent = {};
  }

  /*
   * We index spans by (id, shared, localEndpoint) before processing them. This latter fields
   * (shared, endpoint) are important because in zipkin (specifically B3), a server can share
   * (re-use) the same ID as its client. This impacts processing quite a bit when multiple servers
   * share one span ID.
   *
   * In a Zipkin trace, a parent (client) and child (server) can share the same ID if in an
   * RPC. If two different servers respond to the same client, the only way for us to tell which
   * is which is by endpoint. Our goal is to retain full paths across multiple endpoints. Even
   * though instrumentation should be configured in such a way that a client never sends the same
   * span ID to multiple servers, it can happen. Accordingly, we index defensively including any
   * endpoint data that might be available.
   */
  _index(span) {
    let idKey;
    let parentKey;

    if (span.shared) {
      // we need to classify a shared span by its endpoint in case multiple servers respond to the
      // same ID sent by the client.
      idKey = keyString(span.id, true, span.localEndpoint);
      // the parent of a server span is a client, which is not ambiguous for a given span ID.
      parentKey = span.id;
    } else {
      idKey = span.id;
      parentKey = span.parentId;
    }

    this._spanToParent[idKey] = parentKey;
  }

  /**
   * Processing is taking a span and placing it at the most appropriate place in the trace tree.
   * For example, if this is a server span, it would be a different node, and a child of its client
   * even if they share the same span ID.
   *
   * Processing is defensive of typical problems in span reporting, such as depth-first. For
   * example, depth-first reporting implies you can see spans missing their parent. Hence, the
   * result of processing all spans can be a virtual root node.
   */
  _process(span) {
    const endpoint = span.localEndpoint;
    const key = keyString(span.id, span.shared, span.localEndpoint);
    const noEndpointKey = endpoint ? keyString(span.id, span.shared) : key;

    let parent;
    if (span.shared) {
      // Shared is a server span. It will very likely be on a different endpoint than the client.
      // Clients are not ambiguous by ID, so we don't need to qualify by endpoint.
      parent = span.id;
    } else if (span.parentId) {
      // We are not a root span, and not a shared server span. Proceed in most specific to least.

      // We could be the child of a shared server span (ex a local (intermediate) span on the same
      // endpoint). This is the most specific case, so we try this first.
      parent = keyString(span.parentId, true, endpoint);
      if (this._spanToParent[parent]) {
        this._spanToParent[noEndpointKey] = parent;
      } else {
        // If there's no shared parent, fall back to normal case which is unqualified beyond ID.
        parent = span.parentId;
      }
    } else { // we are root or don't know our parent
      if (this._rootSpan) {
        if (this._debug) {
          const prefix = 'attributing span missing parent to root';
          /* eslint-disable no-console */
          console.log(
            `${prefix}: traceId=${span.traceId}, rootId=${this._rootSpan.span.id}, id=${span.id}`
          );
        }
      }
    }

    const node = new SpanNode(span);
    // special-case root, and attribute missing parents to it. In
    // other words, assume that the first root is the "real" root.
    if (!parent && !this._rootSpan) {
      this._rootSpan = node;
      delete this._spanToParent[noEndpointKey];
    } else if (span.shared) {
      // In the case of shared server span, we need to address it both ways, in case intermediate
      // spans are lacking endpoint information.
      this._keyToNode[key] = node;
      this._keyToNode[noEndpointKey] = node;
    } else {
      this._keyToNode[noEndpointKey] = node;
    }
  }

  /*
   * Builds a trace tree by merging and processing the input or returns an empty tree.
   *
   * While the input can be incomplete or redundant, they must all be a part of the same trace
   * (e.g. all share the same trace ID).
   */
  build(spans) {
    if (spans.length === 0) throw new Error('Trace was empty');

    // In order to make a tree, we need clean data. This will merge any duplicates so that we
    // don't have redundant leaves on the tree.
    const cleaned = mergeV2ById(spans);
    const length = cleaned.length;
    const traceId = cleaned[0].traceId;

    if (this._debug) {
      /* eslint-disable no-console */
      console.log(`building trace tree: traceId=${traceId}`);
    }

    // Next, index all the spans so that we can understand any relationships.
    for (let i = 0; i < length; i++) {
      this._index(cleaned[i]);
    }

    // Now that we've index references to all spans, we can revise any parent-child relationships.
    // Notably, by now, we can tell which is the root-most.
    for (let i = 0; i < length; i++) {
      this._process(cleaned[i]);
    }

    if (!this._rootSpan) {
      if (this._debug) {
        /* eslint-disable no-console */
        console.log(`substituting dummy node for missing root span: traceId=${traceId}`);
      }
      this._rootSpan = new SpanNode();
    }

    // At this point, we have the most reliable parent-child relationships and can allocate spans
    // corresponding the the best place in the trace tree.
    Object.keys(this._spanToParent).forEach(key => {
      const child = this._keyToNode[key];
      const parent = this._keyToNode[this._spanToParent[key]];

      if (!parent) { // Handle headless by attaching spans missing parents to root
        this._rootSpan.addChild(child);
      } else {
        parent.addChild(child);
      }
    });
    return this._rootSpan;
  }
}

module.exports = {
  SpanNode, // for testing
  SpanNodeBuilder
};
