/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/* eslint-disable global-require */

import { afterEach, it, describe, expect, vi } from 'vitest';

// To test BASE_PATH we can't import it above, as it would return a constant
// value for all tests. Instead, we use `await import` later.
describe('BASE_PATH', () => {
  afterEach(() => {
    vi.resetModules();
  });

  it('defaults to /zipkin with no base tag', async () => {
    const { BASE_PATH } = await import('./api');
    expect(BASE_PATH).toEqual('/zipkin');
  });

  it('is set to base tag when present', async () => {
    const base = document.createElement('base');
    base.setAttribute('href', '/coolzipkin/');
    document.head.append(base);

    const { BASE_PATH } = await import('./api');
    expect(BASE_PATH).toEqual('/coolzipkin');
  });
});
