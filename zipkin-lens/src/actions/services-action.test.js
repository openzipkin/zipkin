import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import * as actions from './services-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('services async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create FETCH_SERVICES_SUCCESS when fetching services has been done', () => {
    fetchMock.getOnce(api.SERVICES, {
      body: ['serviceA', 'serviceB', 'serviceC'],
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: types.FETCH_SERVICES_REQUEST },
      {
        type: types.FETCH_SERVICES_SUCCESS,
        services: ['serviceA', 'serviceB', 'serviceC'],
      },
    ];
    const store = mockStore({});

    return store.dispatch(actions.fetchServices()).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
