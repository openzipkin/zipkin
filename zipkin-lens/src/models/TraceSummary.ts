/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
export type ServiceNameAndSpanCount = {
  serviceName: string;
  spanCount: number;
};

type TraceSummary = {
  traceId: string;
  timestamp: number;
  duration: number;
  serviceSummaries: ServiceNameAndSpanCount[];
  infoClass?: string;
  spanCount: number;
  width: number;
  root: {
    serviceName: string;
    spanName: string;
  };
};

export default TraceSummary;
