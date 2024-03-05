/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
