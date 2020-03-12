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
export default [
  {
    traceId: '1e223ff1f80f1c69',
    parentId: '74280ae0c10d8062',
    id: '43210ae0c10d1234',
    name: 'async',
    timestamp: 1470150004008762,
    duration: 65000,
    localEndpoint: {
      serviceName: 'serviceb',
      ipv4: '192.0.0.0',
    },
  },
  {
    traceId: '1e223ff1f80f1c69',
    parentId: 'bf396325699c84bf',
    id: '74280ae0c10d8062',
    kind: 'SERVER',
    name: 'post',
    timestamp: 1470150004008761,
    duration: 93577,
    localEndpoint: {
      serviceName: 'serviceb',
      ipv4: '192.0.0.0',
    },
    shared: true,
  },
  {
    traceId: '1e223ff1f80f1c69',
    id: 'bf396325699c84bf',
    kind: 'SERVER',
    name: 'get',
    timestamp: 1470150004071068,
    duration: 99411,
    localEndpoint: {
      serviceName: 'servicea',
      ipv4: '127.0.0.0',
    },
  },
  {
    traceId: '1e223ff1f80f1c69',
    parentId: 'bf396325699c84bf',
    id: '74280ae0c10d8062',
    kind: 'CLIENT',
    name: 'post',
    timestamp: 1470150004074202,
    duration: 94539,
    localEndpoint: {
      serviceName: 'servicea',
      ipv4: '127.0.0.0',
    },
  },
];
