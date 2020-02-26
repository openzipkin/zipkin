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

/* eslint-disable global-require */

describe('BASE_PATH', () => {
  afterEach(() => {
    jest.resetModules();
  });

  it('defaults to /zipkin with no base tag', () => {
    expect(require('./api').BASE_PATH).toEqual('/zipkin');
  });

  it('is set to base tag when present', () => {
    const base = document.createElement('base');
    base.setAttribute('href', '/coolzipkin/');
    document.head.append(base);
    expect(require('./api').BASE_PATH).toEqual('/coolzipkin');
  });
});
