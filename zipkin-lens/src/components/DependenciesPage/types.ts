/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
export type Edge = {
  source: string;
  target: string;
  metrics: {
    normal: number;
    danger: number;
  };
};
