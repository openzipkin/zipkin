export const ensureV2 = (trace) => {
  if (!Array.isArray(trace) || trace.length === 0) {
    throw new Error('input is not a list');
  }
  const [first] = trace;
  if (!first.traceId || !first.id) {
    throw new Error('List<Span> implies at least traceId and id fields');
  }
  if (first.binaryAnnotations || (!first.localEndpoint && !first.remoteEndpoint && !first.tags)) {
    throw new Error(
      'v1 format is not supported. For help, contact https://gitter.im/openzipkin/zipkin',
    );
  }
};
