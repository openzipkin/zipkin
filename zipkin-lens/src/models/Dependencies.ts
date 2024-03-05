/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
type Dependencies = {
  parent: string;
  child: string;
  callCount: number;
  errorCount?: number;
}[];

export default Dependencies;
