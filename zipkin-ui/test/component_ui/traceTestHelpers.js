export function endpoint(ipv4, port, serviceName) {
  return {ipv4, port, serviceName};
}

export function annotation(timestamp, value, endpoint) {
  return {timestamp, value, endpoint};
}

export function span(traceId, name, id, parentId = null, timestamp = null, duration = null, annotations = [], binaryAnnotations = [], debug = false) {
  return {
    traceId,
    name,
    id,
    parentId,
    timestamp,
    duration,
    annotations,
    binaryAnnotations,
    debug
  };
}
