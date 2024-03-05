/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
const extractBasePath = () => {
  const base = document.getElementsByTagName('base');
  if (base.length === 0) {
    return '/zipkin';
  }
  return base[0]?.getAttribute('href')?.replace(/\/+$/, '');
};

export const BASE_PATH = extractBasePath();

export const ZIPKIN_API = `${BASE_PATH}/api/v2`;
export const UI_CONFIG = `${BASE_PATH}/config.json`;
export const SERVICES = `${ZIPKIN_API}/services`;
export const REMOTE_SERVICES = `${ZIPKIN_API}/remoteServices`;
export const SPANS = `${ZIPKIN_API}/spans`;
export const TRACES = `${ZIPKIN_API}/traces`;
export const TRACE = `${ZIPKIN_API}/trace`;
export const DEPENDENCIES = `${ZIPKIN_API}/dependencies`;
export const AUTOCOMPLETE_KEYS = `${ZIPKIN_API}/autocompleteKeys`;
export const AUTOCOMPLETE_VALUES = `${ZIPKIN_API}/autocompleteValues`;
