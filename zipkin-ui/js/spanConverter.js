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
function toAnnotationRow(a, localFormatted, isDerived = false) {
  const res = {
    isDerived,
    value: ConstantNames[a.value] || a.value,
    timestamp: a.timestamp,
  };
  if (localFormatted) res.endpoint = localFormatted;
  return res;
}

function parseAnnotationRows(span) {
  const localFormatted = formatEndpoint(span.localEndpoint) || undefined;

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
  span.annotations.forEach(a => {
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
  });

  switch (kind) {
    case 'CLIENT':
      begin = 'Client Start';
      end = 'Client Finish';
      break;
    case 'SERVER':
      begin = 'Server Start';
      end = 'Server Finish';
      break;
    case 'PRODUCER':
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

  const beginAnnotation = startTs && begin;
  const endAnnotation = endTs && end;

  const annotations = []; // prefer empty to undefined for arrays

  let annotationCount = annotationsToAdd.length;
  if (beginAnnotation) {
    annotationCount++;
    annotations.push(toAnnotationRow({
      value: begin,
      timestamp: startTs
    }, localFormatted, true));
  }

  annotationsToAdd.forEach((a) => {
    if (beginAnnotation && a.value === begin) return;
    if (endAnnotation && a.value === end) return;
    annotations.push(toAnnotationRow(a, localFormatted));
  });

  if (endAnnotation) {
    annotationCount++;
    annotations.push(toAnnotationRow({
      value: end,
      timestamp: endTs
    }, localFormatted, true));
  }
  return annotations;
}

function parseTagRows(span) {
  const localFormatted = formatEndpoint(span.localEndpoint) || undefined;

  const tagRows = []; // prefer empty to undefined for arrays
  const keys = Object.keys(span.tags);
  if (keys.length > 0) {
    keys.forEach(key => {
      const tagRow = {
        key: ConstantNames[key] || key,
        value: span.tags[key]
      };
      if (localFormatted) tagRow.endpoint = localFormatted;
      tagRows.push(tagRow);
    });
  }

  // Ensure there's at least some data that will display the local address
  if (!span.kind && span.annotations.length === 0 && localFormatted && keys.length === 0) {
    tagRows.push({
      key: 'Local Address',
      value: localFormatted
    });
  }

  let addr;
  switch (span.kind) {
    case 'CLIENT':
      addr = 'Server Address';
      break;
    case 'SERVER':
      addr = 'Client Address';
      break;
    case 'PRODUCER':
      addr = 'Broker Address';
      break;
    case 'CONSUMER':
      addr = 'Broker Address';
      break;
    default:
  }

  if (span.remoteEndpoint) {
    tagRows.push({
      key: addr || 'Server Address', // default when we don't know the endpoint
      value: formatEndpoint(span.remoteEndpoint)
    });
  }
  return tagRows;
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

// This guards to ensure we don't add duplicate service names on merge
function maybePushServiceName(serviceNames, serviceName) {
  if (!serviceName) return;
  if (serviceNames.findIndex(s => s === serviceName) === -1) {
    serviceNames.push(serviceName);
  }
}

function getServiceName(endpoint) {
  return endpoint ? endpoint.serviceName : undefined;
}

// assumes spans are already clean
function merge(spans) {
  const first = spans.shift();
  const res = {
    traceId: first.traceId,
    id: first.id
  };
  if (first.parentId) res.parentId = first.parentId;

  if (first.name) res.name = first.name;

  if (!first.shared) {
    if (first.timestamp) res.timestamp = first.timestamp;
    if (first.duration) res.duration = first.duration;
  }

  const firstServiceName = getServiceName(first.localEndpoint);
  if (firstServiceName) res.serviceName = firstServiceName;
  res.serviceNames = firstServiceName ? [firstServiceName] : [];
  maybePushServiceName(res.serviceNames, getServiceName(first.remoteEndpoint));

  res.annotations = parseAnnotationRows(first);
  res.tags = parseTagRows(first);
  if (first.debug) res.debug = true;

  spans.forEach(next => {
    if (next.parentId) res.parentId = next.parentId;
    if (next.name && (!res.name || next.kind === 'SERVER')) {
      res.name = next.name; // prefer the server's span name
    }

    // If we have 2 different timestamps. Prefer the not shared one
    if (!next.shared) {
      if (!res.timestamp) res.timestamp = next.timestamp;
      if (!res.duration) res.duration = next.duration;
    }

    const nextServiceName = getServiceName(next.localEndpoint);
    if (next.kind === 'SERVER' && nextServiceName) {
      res.serviceName = nextServiceName; // prefer the server's service name
    }

    maybePushServiceName(res.serviceNames, nextServiceName);
    maybePushServiceName(res.serviceNames, getServiceName(next.remoteEndpoint));

    parseAnnotationRows(next).forEach((a) => maybePushAnnotation(res.annotations, a));
    parseTagRows(next).forEach((t) => maybePushTag(res.tags, t));

    if (next.debug) res.debug = true;
  });
  res.annotations.sort((a, b) => a.timestamp - b.timestamp);
  res.serviceNames.sort();
  return res;
}

module.exports.SPAN_V1 = {
  merge(spans) {
    return merge(spans);
  }
};
