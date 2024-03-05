/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
export const ensureV2TraceData = (trace) => {
  if (!Array.isArray(trace) || trace.length === 0) {
    throw new Error('input is not a list');
  }
  const [first] = trace;
  if (!first.traceId || !first.id) {
    throw new Error('List<Span> implies at least traceId and id fields');
  }
  if (
    first.binaryAnnotations ||
    (!first.localEndpoint && !first.remoteEndpoint && !first.tags)
  ) {
    throw new Error(
      'v1 format is not supported. For help, contact https://gitter.im/openzipkin/zipkin',
    );
  }
};

export const hasRootSpan = (trace) => {
  switch (trace.length) {
    case 0:
      return false;
    case 1:
      return true;
    default:
      if (trace[0].depth < trace[1].depth) {
        return true;
      }
      return false;
  }
};
