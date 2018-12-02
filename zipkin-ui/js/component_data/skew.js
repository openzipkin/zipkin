import {SpanNode, SpanNodeBuilder} from './spanNode';

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

  // skew is when the server happens before the client
  if (serverTimestamp > clientTimestamp) return undefined;

  const clientDuration = parent.duration;
  const serverDuration = child.duration;
  if (!clientDuration || !serverDuration) oneWay = true;

  const server = child.localEndpoint;
  if (!server) return undefined;
  const client = parent.localEndpoint;
  if (!client) return undefined;

  // There's no skew if the RPC is going to itself
  if (ipsMatch(server, client)) return undefined;

  if (oneWay) {
    const latency = serverTimestamp - clientTimestamp;

    // the only way there is skew is when the client appears to be after the server
    if (latency > 0) return undefined;
    // We can't currently do better than push the client and server apart by minimum duration (1)
    return new ClockSkew({endpoint: server, skew: latency - 1});
  } else {
    // If the client finished before the server (async), we still know the server must have happened
    // after the client. So, push 1us.
    if (clientDuration < serverDuration) {
      const skew = serverTimestamp - clientTimestamp - 1;
      return new ClockSkew({endpoint: server, skew});
    }

    // We assume latency is half the difference between the client and server duration.
    const latency = (clientDuration - serverDuration) / 2;

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

function treeCorrectedForClockSkew(spans, debug = false) {
  if (spans.length === 0) return new SpanNode();

  const trace = new SpanNodeBuilder({debug}).build(spans);

  if (!trace.span) {
    if (debug) {
      /* eslint-disable no-console */
      console.log(
        `skipping clock skew adjustment due to missing root span: traceId=${spans[0].traceId}`
      );
    }
    return trace;
  }

  const childrenOfRoot = trace.children;
  for (let i = 0; i < childrenOfRoot.length; i++) {
    const next = childrenOfRoot[i].span;
    if (next.parentId || next.shared) continue;

    const traceId = next.traceId;
    const spanId = next.id;
    const rootSpanId = trace.span.id;
    if (debug) {
      /* eslint-disable no-console */
      const prefix = 'skipping redundant root span';
      console.log(`${prefix}: traceId=${traceId}, rootSpanId=${rootSpanId}, spanId=${spanId}`);
    }
    return trace;
  }

  adjust(trace);
  return trace;
}


module.exports = {
  ipsMatch, // for testing
  getClockSkew, // for testing
  treeCorrectedForClockSkew
};
