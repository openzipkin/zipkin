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
import reducer from './traces';
import * as types from '../constants/action-types';

describe('traces reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      traces: [],
    });
  });

  it('should handle FETCH_TRACES_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_TRACES_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      traces: [],
    });
  });

  it('should handle FETCH_TRACES_SUCCESS', () => {
    expect(
      reducer({
        isLoading: true,
        traces: [
          [
            {
              traceId: 'd050e0d52326cf81',
              parentId: 'd050e0d52326cf81',
              id: 'd1ccbada31490783',
              kind: 'CLIENT',
              name: 'getInfoByAccessToken',
              timestamp: 1542337504412859,
              duration: 8667,
              localEndpoint: {
                serviceName: 'serviceA',
                ipv4: '127.0.0.1',
              },
              remoteEndpoint: {
                serviceName: 'serviceB',
                ipv4: '127.0.0.2',
                port: 8080,
              },
            },
          ],
        ],
      }, {
        type: types.FETCH_TRACES_SUCCESS,
        traces: [
          [
            {
              traceId: 'c020e0d52326cf84',
              parentId: 'c020e0d52326cf84',
              id: 'd1fasdfda31490783',
              kind: 'CLIENT',
              name: 'getName',
              timestamp: 1542297504412859,
              duration: 86780,
              localEndpoint: {
                serviceName: 'serviceC',
                ipv4: '127.0.0.3',
              },
              remoteEndpoint: {
                serviceName: 'serviceD',
                ipv4: '127.0.0.4',
                port: 8080,
              },
            },
          ],
        ],
      }),
    ).toEqual({
      isLoading: false,
      traces: [
        [
          {
            traceId: 'c020e0d52326cf84',
            parentId: 'c020e0d52326cf84',
            id: 'd1fasdfda31490783',
            kind: 'CLIENT',
            name: 'getName',
            timestamp: 1542297504412859,
            duration: 86780,
            localEndpoint: {
              serviceName: 'serviceC',
              ipv4: '127.0.0.3',
            },
            remoteEndpoint: {
              serviceName: 'serviceD',
              ipv4: '127.0.0.4',
              port: 8080,
            },
          },
        ],
      ],
    });
  });

  it('should handle FETCH_TRACES_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        traces: [
          [
            {
              traceId: 'c020e0d52326cf84',
              parentId: 'c020e0d52326cf84',
              id: 'd1fasdfda31490783',
              kind: 'CLIENT',
              name: 'getName',
              timestamp: 1542297504412859,
              duration: 86780,
              localEndpoint: {
                serviceName: 'serviceC',
                ipv4: '127.0.0.3',
              },
              remoteEndpoint: {
                serviceName: 'serviceD',
                ipv4: '127.0.0.4',
                port: 8080,
              },
            },
          ],
        ],
      }, {
        type: types.FETCH_TRACES_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      traces: [],
    });
  });
});
