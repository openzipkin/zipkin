/*
 * Copyright 2015-2024 The OpenZipkin Authors
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
