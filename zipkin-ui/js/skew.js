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

  // Returns an array of spans resulting from a breadth-first traversal at this node
  traverse() {
    const result = [];
    const queue = [this];

    while (queue.length > 0) {
      const current = queue.shift();

      // when there's a synthetic root span, the span could be undefined
      if (current.span) result.push(current.span);

      const children = current.children;
      for (let i = 0; i < children.length; i++) {
        queue.push(children[i]);
      }
    }
    return result;
  }

  toString() {
    if (this._span) return `SpanNode(${JSON.stringify(this._span)})`;
    return 'SpanNode()';
  }
}

// In javascript, dict keys can't be objects
function keyString(id, shared = false, endpoint) {
  const endpointString = endpoint ? JSON.stringify(endpoint) : 'x';
  return `${id}-${!!shared}-${endpointString}`;
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
    const shared = !!span.shared; // guards against undefined

    if (shared) {
      // we need to classify a shared span by its endpoint in case multiple servers respond to the
      // same ID sent by the client.
      idKey = keyString(span.id, true, span.localEndpoint);
      // the parent of a server span is a client, which is not ambiguous for a given span ID.
      parentKey = keyString(span.id);
    } else {
      idKey = keyString(span.id, shared);
      if (span.parentId) parentKey = keyString(span.parentId);
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
    if (!!span.shared) {
      // Shared is a server span. It will very likely be on a different endpoint than the client.
      // Clients are not ambiguous by ID, so we don't need to qualify by endpoint.
      parent = keyString(span.id);
    } else if (span.parentId) {
      // We are not a root span, and not a shared server span. Proceed in most specific to least.

      // We could be the child of a shared server span (ex a local (intermediate) span on the same
      // endpoint). This is the most specific case, so we try this first.
      parent = keyString(span.parentId, true, endpoint);
      if (this._spanToParent[parent]) {
        this._spanToParent[noEndpointKey] = parent;
      } else {
        // If there's no shared parent, fall back to normal case which is unqualified beyond ID.
        parent = keyString(span.parentId);
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
    } else if (!!span.shared) {
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
    if (spans.length === 0) throw new Error('spans were empty');

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

class ClockSkew {
  constructor(params) {
    const {endpoint, skew} = params;
    this._endpoint = endpoint;
    this._skew = skew;
  }

  get endpoint() {
    return this._endpoint;
  }

  get skew() {
    return this._skew;
  }
}

function ipsMatch(a, b) {
  if (!a || !b) return false;
  if (a.ipv6 && b.ipv6 && a.ipv6 === b.ipv6) {
    return true;
  }
  if (!a.ipv4 && !b.ipv4) return false;
  return a.ipv4 === b.ipv4;
}

// If any annotation has an IP with skew associated, adjust accordingly.
function adjustTimestamps(span, skew) {
  if (!ipsMatch(skew.endpoint, span.localEndpoint)) return span;

  const result = Object.assign({}, span);
  if (span.timestamp) result.timestamp = span.timestamp - skew.skew;
  const annotationLength = span.annotations.length;
  if (annotationLength > 0) result.annotations = [];
  for (let i = 0; i < annotationLength; i++) {
    const a = span.annotations[i];
    result.annotations[i] = {timestamp: a.timestamp - skew.skew, value: a.value};
  }
  return result;
}

/* Uses span kind to determine if there's clock skew. */
function getClockSkew(node) {
  const parent = node.parent ? node.parent.span : undefined;
  const child = node.span;
  if (!parent) return undefined;

  // skew is only detectable client to server
  if (parent.kind !== 'CLIENT' || child.kind !== 'SERVER') return undefined;

  let oneWay = false;
  const clientTimestamp = parent.timestamp;
  const serverTimestamp = child.timestamp;
  if (!clientTimestamp || !serverTimestamp) return undefined;

  const clientDuration = parent.duration;
  const serverDuration = child.duration;
  if (!clientDuration || !serverDuration) oneWay = true;

  const server = child.localEndpoint;
  if (!server) return undefined;
  const client = parent.localEndpoint;
  if (!client) return undefined;

  // There's no skew if the RPC is going to itself
  if (ipsMatch(server, client)) return undefined;

  let latency;
  if (oneWay) {
    latency = serverTimestamp - clientTimestamp;
    // the only way there is skew is when the client appears to be after the server
    if (latency > 0) return undefined;
    // We can't currently do better than push the client and server apart by minimum duration (1)
    return new ClockSkew({endpoint: server, skew: latency - 1});
  } else {
    // If the client finished before the server (async), we still know the server must have happened
    // after the client. So, push 1us.
    if (clientDuration < serverDuration) {
      return new ClockSkew({endpoint: server, skew: serverTimestamp - clientTimestamp - 1});
    }

    // We assume latency is half the difference between the client and server duration.
    latency = (clientDuration - serverDuration) / 2;

    // We can't see skew when send happens before receive
    if (latency < 0) return undefined;

    const skew = serverTimestamp - latency - clientTimestamp;
    if (skew !== 0) return new ClockSkew({endpoint: server, skew});
  }
  return undefined;
}

/*
 * Recursively adjust the timestamps on the span trace. Root span is the reference point, all
 * children's timestamps gets adjusted based on that span's timestamps.
 */
function adjust(node, skewFromParent) {
  // adjust skew for the endpoint brought over from the parent span
  if (skewFromParent) {
    node.setSpan(adjustTimestamps(node.span, skewFromParent));
  }

  // Is there any skew in the current span?
  let skew = getClockSkew(node);
  if (skew) {
    // the current span's skew may be a different endpoint than its parent, so adjust again.
    node.setSpan(adjustTimestamps(node.span, skew));
  } else if (skewFromParent) {
    // Assumes we are on the same host: propagate skew from our parent
    skew = skewFromParent;
  }
  // propagate skew to any children
  node.children.forEach(child => adjust(child, skew));
}

function correctForClockSkew(spans, debug = false) {
  if (spans.length === 0) return spans;

  const trace = new SpanNodeBuilder({debug}).build(spans);

  if (!trace.span) {
    if (debug) {
      /* eslint-disable no-console */
      console.log(
        `skipping clock skew adjustment due to missing root span: traceId=${spans[0].traceId}`
      );
    }
    return spans;
  }

  const childrenOfRoot = trace.children;
  for (let i = 0; i < childrenOfRoot.length; i++) {
    const next = childrenOfRoot[i].span;
    if (next.parentId || !!next.shared) continue;

    const traceId = next.traceId;
    const spanId = next.id;
    const rootSpanId = trace.span.id;
    /* eslint-disable no-console */
    console.log(
      `skipping redundant root span: traceId=${traceId}, rootSpanId=${rootSpanId}, spanId=${spanId}`
    );
    return spans;
  }

  adjust(trace);
  return trace.traverse();
}


module.exports = {
  SpanNode,
  SpanNodeBuilder,
  ipsMatch, // for testing
  getClockSkew, // for testing
  correctForClockSkew
};
