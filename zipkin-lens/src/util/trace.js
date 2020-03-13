/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
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
