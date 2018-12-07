import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import * as actions from './trace-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('trace async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create FETCH_TRACE_SUCCESS when fetching a trace has been done', () => {
    fetchMock.getOnce(`${api.TRACE}/d050e0d52326cf81`, {
      body: [
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
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: types.FETCH_TRACE_REQUEST },
      {
        type: types.FETCH_TRACE_SUCCESS,
        trace: [
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
      },
    ];
    const store = mockStore({});

    return store.dispatch(actions.fetchTrace('d050e0d52326cf81')).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
