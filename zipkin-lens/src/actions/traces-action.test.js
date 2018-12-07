import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import * as actions from './traces-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('traces actions', () => {
  it('should create an action to clear traces', () => {
    const expectedAction = {
      type: types.CLEAR_TRACES,
    };
    expect(actions.clearTraces()).toEqual(expectedAction);
  });
});

describe('traces async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create FETCH_TRACES_SUCCESS when fetching traces has been done', () => {
    fetchMock.getOnce(`${api.TRACES}?serviceName=serviceA&spanName=span1`, {
      body: [
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
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: types.FETCH_TRACES_REQUEST },
      {
        type: types.FETCH_TRACES_SUCCESS,
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
      },
    ];
    const store = mockStore({});

    return store.dispatch(actions.fetchTraces({
      serviceName: 'serviceA',
      spanName: 'span1',
    })).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
