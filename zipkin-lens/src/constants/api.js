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
const { API_BASE } = process.env;

const inferBasePath = () => {
  const { pathname } = window.location;
  // Infer the path that zipkin is mounted on based on the current path.
  if (pathname.endsWith('/dependency')) {
    return pathname.substring(0, pathname.length - '/dependency'.length);
  }
  if (pathname.endsWith('/traceViewer')) {
    return pathname.substring(0, pathname.length - '/traceViewer'.length);
  }
  const tracesIndex = pathname.lastIndexOf('/traces/');
  if (tracesIndex !== -1) {
    return pathname.substring(0, tracesIndex);
  }

  // Zipkin server always redirects from /zipkin to /zipkin/ but handle the non-redirected path too
  // just in case.
  return pathname.endsWith('/') ? pathname.substring(0, pathname.length - 1) : pathname;
};

export const BASE_PATH = inferBasePath();
export const ZIPKIN_BASE = `${API_BASE || ''}${BASE_PATH}`;

export const ZIPKIN_API = `${ZIPKIN_BASE}/api/v2`;
export const UI_CONFIG = `${ZIPKIN_BASE}/config.json`;
export const SERVICES = `${ZIPKIN_API}/services`;
export const REMOTE_SERVICES = `${ZIPKIN_API}/remoteServices`;
export const SPANS = `${ZIPKIN_API}/spans`;
export const TRACES = `${ZIPKIN_API}/traces`;
export const TRACE = `${ZIPKIN_API}/trace`;
export const DEPENDENCIES = `${ZIPKIN_API}/dependencies`;
export const AUTOCOMPLETE_KEYS = `${ZIPKIN_API}/autocompleteKeys`;
export const AUTOCOMPLETE_VALUES = `${ZIPKIN_API}/autocompleteValues`;
