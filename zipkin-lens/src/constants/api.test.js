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

import { inferBasePath } from './api';

describe('base path', () => {
  let oldLocation;
  beforeEach(() => {
    oldLocation = window.location;
    delete window.location;
  });

  afterEach(() => {
    window.location = oldLocation;
  });

  it('inferred for default dependency URL', () => {
    window.location = { pathname: '/zipkin/dependency'};
    expect(inferBasePath()).toEqual('/zipkin');
  });

  it('inferred for custom dependency URL', () => {
    window.location = { pathname: '/coolzipkin/dependency'};
    expect(inferBasePath()).toEqual('/coolzipkin');
  });

  it('inferred for root dependency URL', () => {
    window.location = { pathname: '/dependency'};
    // When Zipkin is mounted at the root of a domain, there is no base path.
    expect(inferBasePath()).toEqual('');
  });

  it('inferred for default traceViewer URL', () => {
    window.location = { pathname: '/zipkin/traceViewer'};
    expect(inferBasePath()).toEqual('/zipkin');
  });

  it('inferred for custom traceViewer URL', () => {
    window.location = { pathname: '/coolzipkin/traceViewer'};
    expect(inferBasePath()).toEqual('/coolzipkin');
  });

  it('inferred for root traceViewer URL', () => {
    window.location = { pathname: '/traceViewer'};
    // When Zipkin is mounted at the root of a domain, there is no base path.
    expect(inferBasePath()).toEqual('');
  });

  it('inferred for default traces URL', () => {
    window.location = { pathname: '/zipkin/traces/1234567890ABCDEF'};
    expect(inferBasePath()).toEqual('/zipkin');
  });

  it('inferred for custom traces URL', () => {
    window.location = { pathname: '/coolzipkin/traces/1234567890ABCDEF'};
    expect(inferBasePath()).toEqual('/coolzipkin');
  });

  it('inferred for root traces URL', () => {
    window.location = { pathname: '/traces/1234567890ABCDEF'};
    // When Zipkin is mounted at the root of a domain, there is no base path.
    expect(inferBasePath()).toEqual('');
  });

  it('inferred for default index URL', () => {
    window.location = { pathname: '/zipkin/'};
    expect(inferBasePath()).toEqual('/zipkin');
  });

  it('inferred for custom index URL', () => {
    window.location = { pathname: '/coolzipkin/'};
    expect(inferBasePath()).toEqual('/coolzipkin');
  });

  it('inferred for root index URL', () => {
    window.location = { pathname: '/'};
    // When Zipkin is mounted at the root of a domain, there is no base path.
    expect(inferBasePath()).toEqual('');
  });

  it('inferred for default index URL without trailing slash', () => {
    window.location = { pathname: '/zipkin'};
    expect(inferBasePath()).toEqual('/zipkin');
  });
});
