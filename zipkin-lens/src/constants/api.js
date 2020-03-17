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
const { REACT_APP_API_BASE } = process.env;

const extractBasePath = () => {
  const base = document.getElementsByTagName('base');
  if (base.length === 0) {
    return '/zipkin';
  }
  return base[0].getAttribute('href').replace(/\/+$/, '');
};

export const BASE_PATH = extractBasePath();
export const ZIPKIN_BASE = `${REACT_APP_API_BASE || ''}${BASE_PATH}`;

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
