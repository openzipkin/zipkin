import reducer from './autocomplete-keys';
import * as types from '../constants/action-types';

describe('autocomplete-keys reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      autocompleteKeys: [],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_KEYS_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_AUTOCOMPLETE_KEYS_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      autocompleteKeys: [],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_KEYS_SUCCESS', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_AUTOCOMPLETE_KEYS_SUCCESS,
        autocompleteKeys: ['environment', 'phase'],
      }),
    ).toEqual({
      isLoading: false,
      autocompleteKeys: ['environment', 'phase'],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_KEYS_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        autocompleteKeys: ['environment', 'phase'],
      }, {
        type: types.FETCH_AUTOCOMPLETE_KEYS_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      autocompleteKeys: [],
    });
  });
});
