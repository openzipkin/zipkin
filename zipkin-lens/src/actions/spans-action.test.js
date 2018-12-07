import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import * as actions from './spans-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('spans actions', () => {
  it('should create an action to clear spans', () => {
    const expectedAction = {
      type: types.CLEAR_SPANS,
    };
    expect(actions.clearSpans()).toEqual(expectedAction);
  });
});

describe('spans async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create FETCH_SPANS_SUCCESS when fetching spans has been done', () => {
    fetchMock.getOnce(`${api.SPANS}?serviceName=serviceA`, {
      body: ['span1', 'span2', 'span3'],
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: types.FETCH_SPANS_REQUEST },
      {
        type: types.FETCH_SPANS_SUCCESS,
        spans: ['span1', 'span2', 'span3'],
      },
    ];
    const store = mockStore({});

    return store.dispatch(actions.fetchSpans('serviceA')).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
