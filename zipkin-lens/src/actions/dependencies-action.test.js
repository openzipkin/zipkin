import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import * as actions from './dependencies-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('dependencies actions', () => {
  it('should create an action to clear dependencies', () => {
    const expectedAction = {
      type: types.CLEAR_DEPENDENCIES,
    };
    expect(actions.clearDependencies()).toEqual(expectedAction);
  });
});

describe('dependencies async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create FETCH_DEPENDENCIES_SUCCESS when fetching dependencies has been done', () => {
    fetchMock.getOnce(api.DEPENDENCIES, {
      body: [
        {
          parent: 'service1',
          child: 'service2',
          callCount: 100,
          errorCount: 5,
        },
        {
          parent: 'service3',
          child: 'service2',
          callCount: 4,
        },
      ],
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: types.FETCH_DEPENDENCIES_REQUEST },
      {
        type: types.FETCH_DEPENDENCIES_SUCCESS,
        dependencies: [
          {
            parent: 'service1',
            child: 'service2',
            callCount: 100,
            errorCount: 5,
          },
          {
            parent: 'service3',
            child: 'service2',
            callCount: 4,
          },
        ],
      },
    ];
    const store = mockStore({});

    return store.dispatch(actions.fetchDependencies()).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
