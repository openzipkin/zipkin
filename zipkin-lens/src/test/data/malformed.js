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
    traceId: '6cf5624e3452d1bc202a57dad35921c0',
    parentId: 'd65495313f944801',
    id: 'd5cb70daead478dc',
    name: 'kong.header_filter',
    timestamp: 1570005203864000,
  },
  {
    traceId: '6cf5624e3452d1bc202a57dad35921c0',
    parentId: 'd65495313f944801',
    id: '80b8efd52d66af27',
    name: 'kong.body_filter',
    timestamp: 1570005203864000,
  },
  {
    traceId: '6cf5624e3452d1bc202a57dad35921c0',
    parentId: 'ed395cb866d02665',
    id: '2a49858b1f1d506b',
    name: 'kong.rewrite',
    timestamp: 1570005203829000,
  },
  {
    traceId: '6cf5624e3452d1bc202a57dad35921c0',
    parentId: 'd65495313f944801',
    id: '3e53779bdde7c111',
    name: 'kong.access',
    timestamp: 1570005203829000,
    duration: 16000,
  },
  {
    traceId: '6cf5624e3452d1bc202a57dad35921c0',
    parentId: 'ed395cb866d02665',
    id: 'd65495313f944801',
    kind: 'CLIENT',
    name: 'kong.proxy',
    timestamp: 1570005203829000,
    duration: 35000,
    localEndpoint: {
      serviceName: 'rewards',
    },
    remoteEndpoint: {
      port: 80,
    },
    tags: {
      'kong.route': 'aaa20d62-813a-428f-aa40-447324cd30af',
      'kong.service': '872eae11-4571-490b-86d2-59834b93f870',
      'peer.hostname': '',
    },
  },
  {
    traceId: '6cf5624e3452d1bc202a57dad35921c0',
    id: 'ed395cb866d02665',
    kind: 'SERVER',
    name: 'kong.request',
    timestamp: 1570005203829000,
    duration: 35000,
    remoteEndpoint: {
      port: 3771,
    },
    tags: {
      component: 'kong',
      'http.method': 'GET',
      'http.status_code': '200',
      'http.url': '',
      'kong.node.id': '37714786-d34b-4513-a459-8020a1f917de',
    },
  },
  {
    traceId: '6cf5624e3452d1bc202a57dad35921c0',
    parentId: 'd65495313f944801',
    id: '862d0f978033cc53',
    name: 'kong.balancer',
    timestamp: 1570005203845000,
    remoteEndpoint: {
      port: 80,
    },
    tags: {
      'kong.balancer.try': '1',
    },
  },
];
