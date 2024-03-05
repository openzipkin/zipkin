/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { ServiceNameAndSpanCount } from './TraceSummary';

export type AdjustedAnnotation = {
  value: string;
  timestamp: number;
  endpoint: string; // Ex. 'fooo' or 'unknown' on null span.localEndpoint
  relativeTime?: string;
};

export type AdjustedSpan = {
  spanId: string;
  spanName: string; // span.name or 'unknown' on null
  serviceName: string; // span.localEndpoint.serviceName or 'unknown' on null
  parentId?: string;
  childIds: string[];
  serviceNames: string[];
  timestamp: number;
  duration: number;
  durationStr: string;
  tags: {
    key: string;
    value: string;
  }[];
  annotations: AdjustedAnnotation[];
  errorType: string;
  depth: number;
  width: number;
  left: number;
};

type AdjustedTrace = {
  traceId: string;
  serviceNameAndSpanCounts: ServiceNameAndSpanCount[];
  duration: number;
  durationStr: string;
  // the root-most span, when the root is missing
  rootSpan: {
    serviceName: string; // span.localEndpoint.serviceName or 'unknown' on null
    spanName: string; // span.name or 'unknown' on null
  };
  spans: AdjustedSpan[];
};

export default AdjustedTrace;
