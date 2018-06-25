function toV1Endpoint(endpoint) {
  if (endpoint === undefined) {
    return undefined;
  }
  const res = {
    serviceName: endpoint.serviceName || '', // undefined is not allowed in v1
  };
  if (endpoint.ipv4) {
    res.ipv4 = endpoint.ipv4;
  }
  if (endpoint.ipv6) {
    res.ipv6 = endpoint.ipv6;
  }
  if (endpoint.port) {
    res.port = endpoint.port;
  }
  return res;
}

function toV1Annotation(ann, endpoint) {
  const res = {
    value: ann.value,
    timestamp: ann.timestamp,
  };
  if (endpoint) {
    res.endpoint = endpoint;
  }
  return res;
}

// ported from zipkin2.v1.V1SpanConverter
function convertV1(span) {
  const res = {
    traceId: span.traceId,
  };
  if (span.parentId) { // instead of writing "parentId": NULL
    res.parentId = span.parentId;
  }
  res.id = span.id;
  res.name = span.name || ''; // undefined is not allowed in v1
  if (span.debug) {
    res.debug = true;
  }

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
  (span.annotations || []).forEach((a) => {
    switch (a.value) {
      case 'cs':
        kind = 'CLIENT';
        if (a.timestamp < startTs) startTs = a.timestamp;
        break;
      case 'sr':
        kind = 'SERVER';
        if (a.timestamp < startTs) startTs = a.timestamp;
        break;
      case 'ss':
        kind = 'SERVER';
        if (a.timestamp > endTs) endTs = a.timestamp;
        break;
      case 'cr':
        kind = 'CLIENT';
        if (a.timestamp > endTs) endTs = a.timestamp;
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
    }
  });

  let addr = 'sa'; // default which will be unset later if needed

  switch (kind) {
    case 'CLIENT':
      addr = 'sa';
      begin = 'cs';
      end = 'cr';
      break;
    case 'SERVER':
      addr = 'ca';
      begin = 'sr';
      end = 'ss';
      break;
    case 'PRODUCER':
      addr = 'ma';
      begin = 'ms';
      end = 'ws';
      if (startTs === 0 || (msTs !== 0 && msTs < startTs)) {
        startTs = msTs;
      }
      if (endTs === 0 || (wsTs !== 0 && wsTs > endTs)) {
        endTs = wsTs;
      }
      break;
    case 'CONSUMER':
      addr = 'ma';
      if (startTs === 0 || (wrTs !== 0 && wrTs < startTs)) {
        startTs = wrTs;
      }
      if (endTs === 0 || (mrTs !== 0 && mrTs > endTs)) {
        endTs = mrTs;
      }
      if (endTs !== 0 || wrTs !== 0) {
        begin = 'wr';
        end = 'mr';
      } else {
        begin = 'mr';
      }
      break;
    default:
  }

  // If we didn't find a span kind, directly or indirectly, unset the addr
  if (!span.remoteEndpoint) addr = undefined;

  const beginAnnotation = startTs && begin;
  const endAnnotation = endTs && end;
  const ep = toV1Endpoint(span.localEndpoint);

  res.annotations = []; // prefer empty to undefined for arrays

  let annotationCount = (span.annotations || []).length;
  if (beginAnnotation) {
    annotationCount++;
    res.annotations.push(toV1Annotation({
      value: begin,
      timestamp: startTs
    }, ep));
  }

  (span.annotations || []).forEach((a) => {
    if (beginAnnotation && a.value === begin) return;
    if (endAnnotation && a.value === end) return;
    res.annotations.push(toV1Annotation(a, ep));
  });

  if (endAnnotation) {
    annotationCount++;
    res.annotations.push(toV1Annotation({
      value: end,
      timestamp: endTs
    }, ep));
  }

  res.binaryAnnotations = []; // prefer empty to undefined for arrays
  const keys = Object.keys(span.tags || {});
  if (keys.length > 0) {
    res.binaryAnnotations = keys.map(key => ({
      key,
      value: span.tags[key],
      endpoint: ep
    }));
  }

  const writeLocalComponent = annotationCount === 0 && ep && keys.length === 0;
  const hasRemoteEndpoint = addr && span.remoteEndpoint;

  // write an empty "lc" annotation to avoid missing the localEndpoint in an in-process span
  if (writeLocalComponent) {
    res.binaryAnnotations.push({key: 'lc', value: '', endpoint: ep});
  }
  if (hasRemoteEndpoint) {
    const address = {
      key: addr,
      value: true,
      endpoint: toV1Endpoint(span.remoteEndpoint)
    };
    res.binaryAnnotations.push(address);
  }

  return res;
}

function merge(left, right) {
  const res = {
    traceId: left.traceId,
    parentId: left.parentId,
    id: left.id
  };
  if (right.parentId) {
    res.parentId = right.parentId;
  } else if (!res.parentId) {
    delete(res.parentId);
  }

  const leftClientSpan = left.annotations.findIndex(a => a.value === 'cr') !== -1;
  const rightServerSpan = right.annotations.findIndex(a => a.value === 'sr') !== -1;

  if (left.name === '' || left.name === 'unknown') {
    res.name = right.name;
  } else if (right.name === '' || right.name === 'unknown') {
    res.name = left.name;
  } else if (leftClientSpan && rightServerSpan) {
    res.name = right.name; // prefer the server's span name
  } else {
    res.name = left.name;
  }

  if (right.timestamp) {
    res.timestamp = right.timestamp;
  } else {
    delete(res.timestamp);
  }
  if (right.duration) {
    res.duration = right.duration;
  } else {
    delete(res.duration);
  }
  res.annotations = left.annotations
                        .concat(right.annotations)
                        .sort((l, r) => l.timestamp - r.timestamp);
  res.binaryAnnotations = left.binaryAnnotations
                              .concat(right.binaryAnnotations);

  if (right.debug) {
    res.debug = true;
  }
  return res;
}

module.exports.SPAN_V1 = {
  convert(span) {
    return convertV1(span);
  },
  merge(left, right) {
    return merge(left, right);
  }
};
