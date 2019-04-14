import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import * as actions from './remote-services-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('remoteServices actions', () => {
  it('should create an action to clear remote services', () => {
    const expectedAction = {
      type: types.CLEAR_REMOTE_SERVICES,
    };
    expect(actions.clearRemoteServices()).toEqual(expectedAction);
  });
});

describe('remoteServices async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create FETCH_REMOTE_SERVICES_SUCCESS when fetching remote services has been done', () => {
    fetchMock.getOnce(`${api.REMOTE_SERVICES}?serviceName=serviceA`, {
      body: ['remoteService1', 'remoteService2', 'remoteService3'],
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: types.FETCH_REMOTE_SERVICES_REQUEST },
      {
        type: types.FETCH_REMOTE_SERVICES_SUCCESS,
        remoteServices: ['remoteService1', 'remoteService2', 'remoteService3'],
      },
    ];
    const store = mockStore({});

    return store.dispatch(actions.fetchRemoteServices('serviceA')).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
