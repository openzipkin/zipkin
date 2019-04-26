/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
const { API_BASE } = process.env;

export const ZIPKIN_API = `${API_BASE || ''}/zipkin/api/v2`;
export const SERVICES = `${ZIPKIN_API}/services`;
export const REMOTE_SERVICES = `${ZIPKIN_API}/remoteServices`;
export const SPANS = `${ZIPKIN_API}/spans`;
export const TRACES = `${ZIPKIN_API}/traces`;
export const TRACE = `${ZIPKIN_API}/trace`;
export const DEPENDENCIES = `${ZIPKIN_API}/dependencies`;
export const AUTOCOMPLETE_KEYS = `${ZIPKIN_API}/autocompleteKeys`;
export const AUTOCOMPLETE_VALUES = `${ZIPKIN_API}/autocompleteValues`;
