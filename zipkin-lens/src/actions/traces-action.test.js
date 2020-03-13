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
import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import * as actions from './traces-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';
import {
  traceSummary as buildTraceSummary,
  traceSummaries as buildTraceSummaries,
  treeCorrectedForClockSkew,
} from '../zipkin';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('traces async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  const rawTraces = [
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
  ];

  it('create TRACES_LOAD_SUCCESS when fetching traces has been done', () => {
    fetchMock.getOnce(`${api.TRACES}?serviceName=serviceA&spanName=span1`, {
      body: rawTraces,
      headers: {
        'content-type': 'application/json',
      },
    });

    const correctedTraces = rawTraces.map(treeCorrectedForClockSkew);
    const correctedTraceMap = {};
    correctedTraces.forEach((trace, index) => {
      const [{ traceId }] = rawTraces[index];
      correctedTraceMap[traceId] = trace;
    });
    const traceSummaries = buildTraceSummaries(
      null,
      correctedTraces.map(buildTraceSummary),
    );

    const expectedActions = [
      {
        type: types.TRACES_LOAD_REQUEST,
      },
      {
        type: types.TRACES_LOAD_SUCCESS,
        traces: rawTraces,
        correctedTraceMap,
        traceSummaries,
        lastQueryParams: {
          serviceName: 'serviceA',
          spanName: 'span1',
        },
      },
    ];
    const store = mockStore({});

    return store
      .dispatch(
        actions.loadTraces({
          serviceName: 'serviceA',
          spanName: 'span1',
        }),
      )
      .then(() => {
        expect(store.getActions()).toEqual(expectedActions);
      });
  });

  it('clearTraces dispatches action', () => {
    const store = mockStore({});

    store.dispatch(actions.clearTraces());
    expect(store.getActions()).toEqual([{ type: types.CLEAR_TRACES }]);
  });
});
