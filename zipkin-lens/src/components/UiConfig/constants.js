/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
export const defaultConfig = {
  environment: '',
  queryLimit: 10,
  defaultLookback: 15 * 60 * 1000, // 15 minutes
  searchEnabled: true,
  dependency: {
    lowErrorRate: 0.5, // 50% of calls in error turns line yellow
    highErrorRate: 0.75, // 75% of calls in error turns line red
    enabled: true,
  },
};
