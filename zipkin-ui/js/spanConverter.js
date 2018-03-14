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
  if (endpoint.port) {
    res.port = endpoint.port;
  }
  return res;
}

function toV1Annotation(ann, endpoint) {
  return {
    value: ann.value,
    timestamp: ann.timestamp,
    endpoint
  };
}

// Copied from https://github.com/openzipkin/zipkin-js/blob/8018e441d01804b02d0d217f10cd82759e71e02a/packages/zipkin/src/jsonEncoder.js#L25
// Modified to correct assumption that 'annotations' always exist and ensure
// that 'beginAnnotation' comes first timestamp/duration should always be copied over
function convertV1(span) {
  const res = {
    traceId: span.traceId
  };
  if (span.parentId) { // instead of writing "parentId": NULL
    res.parentId = span.parentId;
  }
  res.id = span.id;
  res.name = span.name || ''; // undefined is not allowed in v1
  res.timestamp = span.timestamp;
  res.duration = span.duration;

  const jsonEndpoint = toV1Endpoint(span.localEndpoint);

  let beginAnnotation;
  let endAnnotation;
  let addressKey;
  switch (span.kind) {
    case 'CLIENT':
      beginAnnotation = span.timestamp ? 'cs' : undefined;
      endAnnotation = 'cr';
      addressKey = 'sa';
      break;
    case 'SERVER':
      beginAnnotation = span.timestamp ? 'sr' : undefined;
      endAnnotation = 'ss';
      addressKey = 'ca';
      break;
    default:
  }

  if (span.annotations !== undefined && span.annotations.length > 0
          || beginAnnotation) { // don't write empty array
    res.annotations = [];
  }

  if (beginAnnotation) {
    res.annotations.push({
      value: beginAnnotation,
      timestamp: span.timestamp,
      endpoint: jsonEndpoint
    });
  }

  if (span.annotations !== undefined && span.annotations.length > 0) {
    span.annotations.forEach((ann) =>
      res.annotations.push(toV1Annotation(ann, jsonEndpoint))
    );
  }

  if (beginAnnotation && span.duration) {
    res.annotations.push({
      value: endAnnotation,
      timestamp: span.timestamp + span.duration,
      endpoint: jsonEndpoint
    });
  }

  const keys = Object.keys(span.tags || {});
  if (keys.length > 0 || span.remoteEndpoint) { // don't write empty array
    res.binaryAnnotations = keys.map(key => ({
      key,
      value: span.tags[key],
      endpoint: jsonEndpoint
    }));
  }

  if (span.remoteEndpoint) {
    const address = {
      key: addressKey,
      value: true,
      endpoint: toV1Endpoint(span.remoteEndpoint)
    };
    res.binaryAnnotations.push(address);
  }

  if (span.debug) { // instead of writing "debug": false
    res.debug = true;
  }
  return res;
}

module.exports.SPAN_V1 = {
  convert(span) {
    return convertV1(span);
  }
};
