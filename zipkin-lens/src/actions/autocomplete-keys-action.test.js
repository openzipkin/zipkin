import configureMockStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import fetchMock from 'fetch-mock';

import * as actions from './autocomplete-keys-action';
import * as types from '../constants/action-types';
import * as api from '../constants/api';

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe('autocomplete-keys async actions', () => {
  afterEach(() => {
    fetchMock.restore();
  });

  it('create FETCH_AUTOCOMPLETE_KEYS_SUCCESS when fetchin autocomplete keys has been done', () => {
    fetchMock.getOnce(api.AUTOCOMPLETE_KEYS, {
      body: ['environment', 'phase'],
      headers: {
        'content-type': 'application/json',
      },
    });

    const expectedActions = [
      { type: types.FETCH_AUTOCOMPLETE_KEYS_REQUEST },
      {
        type: types.FETCH_AUTOCOMPLETE_KEYS_SUCCESS,
        autocompleteKeys: ['environment', 'phase'],
      },
    ];
    const store = mockStore();

    return store.dispatch(actions.fetchAutocompleteKeys()).then(() => {
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});
