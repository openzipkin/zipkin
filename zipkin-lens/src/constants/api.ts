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
export const BASE_PATH = import.meta.env.BASE_PATH;
export const ZIPKIN_API = `${import.meta.env.API_BASE}/api/v2`;
export const UI_CONFIG = `${BASE_PATH}/config.json`;
export const SERVICES = `${ZIPKIN_API}/services`;
export const REMOTE_SERVICES = `${ZIPKIN_API}/remoteServices`;
export const SPANS = `${ZIPKIN_API}/spans`;
export const TRACES = `${ZIPKIN_API}/traces`;
export const TRACE = `${ZIPKIN_API}/trace`;
export const DEPENDENCIES = `${ZIPKIN_API}/dependencies`;
export const AUTOCOMPLETE_KEYS = `${ZIPKIN_API}/autocompleteKeys`;
export const AUTOCOMPLETE_VALUES = `${ZIPKIN_API}/autocompleteValues`;
