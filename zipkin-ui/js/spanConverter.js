import _ from 'lodash';
import {compare, normalizeTraceId} from './spanCleaner';
import {ConstantNames} from './component_ui/traceConstants';

export function formatEndpoint(endpoint) {
  if (!endpoint) return undefined;
  const {ipv4, ipv6, port, serviceName} = endpoint;
  if (ipv4 || ipv6) {
    const ip = ipv6 ? `[${ipv6}]` : ipv4; // arbitrarily prefer ipv6
    const portString = port ? `:${port}` : '';
    const serviceNameString = serviceName ? ` (${serviceName})` : '';
    return ip + portString + serviceNameString;
  } else {
    return serviceName || '';
  }
}

/*
 * Derived means not annotated directly. Ex 'Server Start' reflects the the timestamp of a
 * kind=SERVER span. 'Server Finish' is timestamp+duration of the same.
 */
function toV1Annotation(a, localFormatted, isDerived = false) {
  const res = {
    isDerived,
    value: ConstantNames[a.value] || a.value,
    timestamp: a.timestamp,
  };
  if (localFormatted) {
    res.endpoint = localFormatted;
  }
  return res;
}

// ported from zipkin2.v1.V1SpanConverter
function convertV1(span) {
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
  if (span.name) res.name = span.name;
  if (span.debug) res.debug = true;

  // Don't report timestamp and duration on shared spans (should be server, but not necessarily)
  if (!span.shared) {
    if (span.timestamp) res.timestamp = span.timestamp;
    if (span.duration) res.duration = span.duration;
  }

  let startTs = span.timestamp || 0;
  let endTs = startTs && span.duration ? startTs + span.duration : 0;
  let msTs = 0;
  let wsTs = 0;
  let wrTs = 0;
  let mrTs = 0;

  let begin;
  let end;

  let kind = span.kind;

  // scan annotations in case there are better timestamps, or inferred kind
  const annotationsToAdd = [];
  const annotationLength = span.annotations ? span.annotations.length : 0;
  for (let i = 0; i < annotationLength; i++) {
    const a = span.annotations[i];
    switch (a.value) {
      case 'cs':
        kind = 'CLIENT';
        if (a.timestamp <= startTs) {
          startTs = a.timestamp;
        } else {
          annotationsToAdd.push(a);
        }
        break;
      case 'sr':
        kind = 'SERVER';
        if (a.timestamp <= startTs) {
          startTs = a.timestamp;
        } else {
          annotationsToAdd.push(a);
        }
        break;
      case 'ss':
        kind = 'SERVER';
        if (a.timestamp >= endTs) {
          endTs = a.timestamp;
        } else {
          annotationsToAdd.push(a);
        }
        break;
      case 'cr':
        kind = 'CLIENT';
        if (a.timestamp >= endTs) {
          endTs = a.timestamp;
        } else {
          annotationsToAdd.push(a);
        }
        break;
      case 'ms':
        kind = 'PRODUCER';
        msTs = a.timestamp;
        break;
      case 'mr':
        kind = 'CONSUMER';
        mrTs = a.timestamp;
        break;
      case 'ws':
        wsTs = a.timestamp;
        break;
      case 'wr':
        wrTs = a.timestamp;
        break;
      default:
        annotationsToAdd.push(a);
    }
  }

  let addr = 'Server Address'; // default which will be unset later if needed

  switch (kind) {
    case 'CLIENT':
      addr = 'Server Address';
      begin = 'Client Start';
      end = 'Client Finish';
      break;
    case 'SERVER':
      addr = 'Client Address';
      begin = 'Server Start';
      end = 'Server Finish';
      break;
    case 'PRODUCER':
      addr = 'Broker Address';
      begin = 'Producer Start';
      end = 'Producer Finish';
      if (startTs === 0 || (msTs !== 0 && msTs < startTs)) {
        startTs = msTs;
        msTs = 0;
      }
      if (endTs === 0 || (wsTs !== 0 && wsTs > endTs)) {
        endTs = wsTs;
        wsTs = 0;
      }
      break;
    case 'CONSUMER':
      addr = 'Broker Address';
      if (startTs === 0 || (wrTs !== 0 && wrTs < startTs)) {
        startTs = wrTs;
        wrTs = 0;
      }
      if (endTs === 0 || (mrTs !== 0 && mrTs > endTs)) {
        endTs = mrTs;
        mrTs = 0;
      }
      if (endTs !== 0 || wrTs !== 0) {
        begin = 'Consumer Start';
        end = 'Consumer Finish';
      } else {
        begin = 'Consumer Start';
      }
      break;
    default:
  }

  // restore sometimes special-cased annotations
  if (msTs) annotationsToAdd.push({timestamp: msTs, value: 'ms'});
  if (wsTs) annotationsToAdd.push({timestamp: wsTs, value: 'ws'});
  if (wrTs) annotationsToAdd.push({timestamp: wrTs, value: 'wr'});
  if (mrTs) annotationsToAdd.push({timestamp: mrTs, value: 'mr'});

  // If we didn't find a span kind, directly or indirectly, unset the addr
  if (!span.remoteEndpoint) addr = undefined;

  const beginAnnotation = startTs && begin;
  const endAnnotation = endTs && end;

  res.annotations = []; // prefer empty to undefined for arrays

  const localFormatted = formatEndpoint(span.localEndpoint) || undefined;
  let annotationCount = annotationsToAdd.length;
  if (beginAnnotation) {
    annotationCount++;
    res.annotations.push(toV1Annotation({
      value: begin,
      timestamp: startTs
    }, localFormatted, true));
  }

  annotationsToAdd.forEach((a) => {
    if (beginAnnotation && a.value === begin) return;
    if (endAnnotation && a.value === end) return;
    res.annotations.push(toV1Annotation(a, localFormatted));
  });

  if (endAnnotation) {
    annotationCount++;
    res.annotations.push(toV1Annotation({
      value: end,
      timestamp: endTs
    }, localFormatted, true));
  }

  res.tags = []; // prefer empty to undefined for arrays
  const keys = Object.keys(span.tags || {});
  if (keys.length > 0) {
    keys.forEach(key => {
      res.tags.push({
        key: ConstantNames[key] || key,
        value: span.tags[key],
        endpoint: localFormatted
      });
    });
  }

  // write a binary annotation when no tags are present to avoid having no context for a local span
  if (annotationCount === 0 && localFormatted && keys.length === 0) {
    res.tags.push({
      key: 'Local Address',
      value: localFormatted
    });
  }

  if (addr && span.remoteEndpoint) {
    res.tags.push({
      key: addr,
      value: formatEndpoint(span.remoteEndpoint)
    });
  }

  res.serviceNames = [];
  if (span.localEndpoint && span.localEndpoint.serviceName) {
    res.serviceName = span.localEndpoint.serviceName;
    res.serviceNames.push(span.localEndpoint.serviceName);
  }
  if (span.remoteEndpoint && span.remoteEndpoint.serviceName) {
    res.serviceNames.push(span.remoteEndpoint.serviceName);
  }
  return res;
}

// This guards to ensure we don't add duplicate annotations on merge
function maybePushAnnotation(annotations, a) {
  if (annotations.findIndex(b => a.value === b.value) === -1) {
    annotations.push(a);
  }
}

// This guards to ensure we don't add duplicate binary annotations on merge
function maybePushTag(tags, a) {
  if (tags.findIndex(b => a.key === b.key) === -1) {
    tags.push(a);
  }
}

function merge(left, right) {
  // normalize ID lengths in case dirty input is received
  //  (this won't be the case from the normal zipkin server, as it normalizes IDs)
  const res = {
    traceId: normalizeTraceId(right.traceId.length > 16 ? right.traceId : left.traceId)
  };

  // take care not to create self-referencing spans even if the input data is incorrect
  const id = left.id.padStart(16, '0');
  if (left.parentId) {
    const leftParent = left.parentId.padStart(16, '0');
    if (leftParent !== id) {
      res.parentId = leftParent;
    }
  }

  if (right.parentId && !res.parentId) {
    const rightParent = right.parentId.padStart(16, '0');
    if (rightParent !== id) {
      res.parentId = rightParent;
    }
  }

  res.id = id;
  if (left.name) res.name = left.name;

  // When we move to span model 2, remove this code in favor of using Span.kind == CLIENT
  let leftClientSpan;
  let rightClientSpan;
  let rightServerSpan;

  res.annotations = [];

  (left.annotations || []).forEach((a) => {
    if (a.value === 'Client Start') leftClientSpan = true;
    maybePushAnnotation(res.annotations, a);
  });

  (right.annotations || []).forEach((a) => {
    if (a.value === 'Client Start') rightClientSpan = true;
    if (a.value === 'Server Start') rightServerSpan = true;
    maybePushAnnotation(res.annotations, a);
  });

  res.annotations.sort((a, b) => a.timestamp - b.timestamp);

  res.tags = [];

  (left.tags || []).forEach((b) => {
    maybePushTag(res.tags, b);
  });

  (right.tags || []).forEach((b) => {
    maybePushTag(res.tags, b);
  });

  if (right.name) {
    if (!res.name || rightServerSpan) {
      res.name = right.name; // prefer the server's span name
    }
  }

  // Single timestamp makes duration easy: just choose max
  if (!left.timestamp || !right.timestamp || left.timestamp === right.timestamp) {
    res.timestamp = left.timestamp || right.timestamp;
    if (!left.duration) {
      res.duration = right.duration;
    } else if (right.duration) {
      res.duration = Math.max(left.duration, right.duration);
    } else {
      res.duration = left.duration;
    }
  } else {
    // We have 2 different timestamps. If we have client data in either one of them, use right,
    // else set timestamp and duration to null
    if (rightClientSpan) {
      res.timestamp = right.timestamp;
      res.duration = right.duration;
    } else if (leftClientSpan) {
      res.timestamp = left.timestamp;
      res.duration = left.duration;
    }
  }

  if (right.debug) {
    res.debug = true;
  }

  if (left.serviceName) {
    // in a shared span, prefer the server's name
    res.serviceName = right.serviceName && leftClientSpan ? right.serviceName : left.serviceName;
  } else if (right.serviceName) {
    res.serviceName = right.serviceName;
  }

  // however, order the client-side service name first for consistency
  res.serviceNames = leftClientSpan
    ? _.union(left.serviceNames, right.serviceNames)
    : _.union(right.serviceNames, left.serviceNames);
  res.serviceNames = _(res.serviceNames).uniq().value();

  return res;
}

/*
 * Instrumentation should set {@link Span#timestamp} when recording a span so that guess-work
 * isn't needed. Since a lot of instrumentation don't, we have to make some guesses.
 *
 * * If there is a 'cs', use that
 * * Fall back to 'sr'
 * * Otherwise, return undefined
 */
// originally zipkin.internal.ApplyTimestampAndDuration.guessTimestamp
function guessTimestamp(span) {
  if (span.timestamp || !span.annotations || span.annotations.length === 0) {
    return span.timestamp;
  }
  let rootServerRecv;
  for (let i = 0; i < span.annotations.length; i++) {
    const a = span.annotations[i];
    if (a.value === 'Client Start') {
      return a.timestamp;
    } else if (a.value === 'Server Start') {
      rootServerRecv = a.timestamp;
    }
  }
  return rootServerRecv;
}

/*
 * For RPC two-way spans, the duration between 'cs' and 'cr' is authoritative. RPC one-way spans
 * lack a response, so the duration is between 'cs' and 'sr'. We special-case this to avoid
 * setting incorrect duration when there's skew between the client and the server.
 */
// originally zipkin.internal.ApplyTimestampAndDuration.apply
function applyTimestampAndDuration(span) {
  // Don't overwrite authoritatively set timestamp and duration!
  if ((span.timestamp && span.duration) || !span.annotations) {
    return span;
  }

  // We cannot backfill duration on a span with less than two annotations. However, we can
  // backfill timestamp.
  const annotationLength = span.annotations.length;
  if (annotationLength < 2) {
    if (span.timestamp) return span;
    const guess = guessTimestamp(span);
    if (!guess) return span;
    span.timestamp = guess; // eslint-disable-line no-param-reassign
    return span;
  }

  // Prefer RPC one-way (cs -> sr) vs arbitrary annotations.
  let first = span.annotations[0].timestamp;
  let last = span.annotations[annotationLength - 1].timestamp;
  span.annotations.forEach((a) => {
    if (a.value === 'Client Start') {
      first = a.timestamp;
    } else if (a.value === 'Client Finish') {
      last = a.timestamp;
    }
  });

  if (!span.timestamp) {
    span.timestamp = first; // eslint-disable-line no-param-reassign
  }
  if (!span.duration && last !== first) {
    span.duration = last - first; // eslint-disable-line no-param-reassign
  }
  return span;
}

/*
 * v1 spans can be sent in multiple parts. Also client and server spans can share the same ID. This
 * merges both scenarios.
 */
// originally zipkin.internal.MergeById.apply
function mergeById(spans) {
  const result = [];

  if (!spans || spans.length === 0) return result;

  const spanIdToSpans = {};
  spans.forEach((s) => {
    const id = s.id.padStart(16, '0');
    spanIdToSpans[id] = spanIdToSpans[id] || [];
    spanIdToSpans[id].push(s);
  });

  Object.keys(spanIdToSpans).forEach(id => {
    const spansToMerge = spanIdToSpans[id];
    let left = spansToMerge[0];
    for (let i = 1; i < spansToMerge.length; i++) {
      left = merge(left, spansToMerge[i]);
    }

    // attempt to get a timestamp so that sorting will be helpful
    result.push(applyTimestampAndDuration(left));
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

module.exports.SPAN_V1 = {
  // Temporary convenience function until functionality is ported to v2
  convertTrace(v2Trace) {
    const v1Trace = v2Trace.map(convertV1);
    return mergeById(v1Trace);
  },
  convert(v2Span) {
    return convertV1(v2Span);
  },
  merge(v1Left, v1Right) {
    return merge(v1Left, v1Right);
  },
  applyTimestampAndDuration(v1Span) {
    return applyTimestampAndDuration(v1Span);
  },
  mergeById(v1Spans) {
    return mergeById(v1Spans);
  }
};
