import _ from 'lodash';

export function normalizeTraceId(traceId) {
  if (traceId.length > 16) {
    const result = traceId.padStart(32, '0');
    // undo prefix if it will result in a 64-bit trace ID
    if (result.startsWith('0000000000000000')) return result.substring(16);
    return result;
  }
  return traceId.padStart(16, '0');
}

function isEndpoint(endpoint) {
  return endpoint && Object.keys(endpoint).length > 0;
}

// This cleans potential dirty v2 inputs, like normalizing IDs etc.
function clean(span) {
  const res = {
    traceId: normalizeTraceId(span.traceId)
  };

  // take care not to create self-referencing spans even if the input data is incorrect
  const id = span.id.padStart(16, '0');
  if (span.parentId) {
    const parentId = span.parentId.padStart(16, '0');
    if (parentId !== id) {
      res.parentId = parentId;
    }
  }
  res.id = id;

  if (span.name && span.name !== '' && span.name !== 'unknown') res.name = span.name;
  if (span.kind) res.kind = span.kind;

  if (span.timestamp) res.timestamp = span.timestamp;
  if (span.duration) res.duration = span.duration;

  if (isEndpoint(span.localEndpoint)) res.localEndpoint = span.localEndpoint;
  if (isEndpoint(span.remoteEndpoint)) res.remoteEndpoint = span.remoteEndpoint;

  res.annotations = span.annotations || [];
  res.annotations.sort((a, b) => a.timestamp - b.timestamp);

  res.tags = span.tags || {};

  if (span.debug) res.debug = true;
  if (span.shared) res.shared = true;

  return res;
}

function merge(left, right) {
  const res = {
    traceId: right.traceId.length > 16 ? right.traceId : left.traceId
  };

  const parentId = left.parentId || right.parentId;
  if (parentId) res.parentId = parentId;

  res.id = left.id;
  const name = left.name || right.name;
  if (name) res.name = name;

  const kind = left.kind || right.kind;
  if (kind) res.kind = kind;

  const timestamp = left.timestamp || right.timestamp;
  if (timestamp) res.timestamp = timestamp;
  const duration = left.duration || right.duration;
  if (duration) res.duration = duration;

  const localEndpoint = Object.assign({}, left.localEndpoint, right.localEndpoint);
  if (isEndpoint(localEndpoint)) res.localEndpoint = localEndpoint;
  const remoteEndpoint = Object.assign({}, left.remoteEndpoint, right.remoteEndpoint);
  if (isEndpoint(remoteEndpoint)) res.remoteEndpoint = remoteEndpoint;

  if (left.annotations.length === 0) {
    res.annotations = right.annotations;
  } else if (right.annotations.length === 0) {
    res.annotations = left.annotations;
  } else {
    res.annotations = _(left.annotations.concat(right.annotations))
      .sortBy('timestamp', 'value')
      .sortedUniqBy('timestamp', 'value').value();
  }

  res.tags = Object.assign({}, left.tags, right.tags);

  if (left.debug || right.debug) res.debug = true;
  if (left.shared || right.shared) res.shared = true;
  return res;
}

// compares potentially undefined input
export function compare(a, b) {
  if (!a && !b) return 0;
  if (!a) return -1;
  if (!b) return 1;
  return (a > b) - (a < b);
}

/*
 * Spans can be sent in multiple parts. Also client and server spans can share the same ID. This
 * merges both scenarios.
 */
export function mergeV2ById(trace) {
  const result = [];

  if (!trace || trace.length === 0) return result;

  // this will be the longest trace ID, in case instrumentation report different lengths
  let traceId;

  const spanIdToSpans = {};
  trace.forEach((s) => {
    const span = clean(s);
    if (!traceId || span.traceId.length > traceId.length) traceId = span.traceId;

    // Make sure IDs are grouped together for merging incomplete span data
    //
    // Only time IDs may be shared are for server-side of RPC span. We check
    // for the shared flag as it is often in the trace context, and by
    // extension also recorded even on incomplete data.
    const key = span.id + span.shared;
    spanIdToSpans[key] = spanIdToSpans[key] || [];
    spanIdToSpans[key].push(span);
  });

  Object.keys(spanIdToSpans).forEach(key => {
    const spansToMerge = spanIdToSpans[key];
    let left = spansToMerge[0];
    left.traceId = traceId;
    for (let i = 1; i < spansToMerge.length; i++) {
      left = merge(left, spansToMerge[i]);
    }
    result.push(left);
  });

  // sort by timestamp, then name, root first in case of skew
  // TODO: this should be a topological sort
  return result.sort((a, b) => {
    if (!a.parentId) { // a is root
      return -1;
    } else if (!b.parentId) { // b is root
      return 1;
    }
    // Either a and b are root or neither are. In any case sort by timestamp, then name
    return compare(a.timestamp, b.timestamp) || compare(a.name, b.name);
  });
}
