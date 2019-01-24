import reducer from './autocomplete-values';
import * as types from '../constants/action-types';

describe('autocomplete-values reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      autocompleteValues: [],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_VALUES_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_AUTOCOMPLETE_VALUES_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      autocompleteValues: [],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_VALUES_SUCCESS', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_AUTOCOMPLETE_VALUES_SUCCESS,
        autocompleteValues: ['alpha', 'beta', 'release'],
      }),
    ).toEqual({
      isLoading: false,
      autocompleteValues: ['alpha', 'beta', 'release'],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_VALUES_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        autocompleteValues: ['alpha', 'beta', 'release'],
      }, {
        type: types.FETCH_AUTOCOMPLETE_VALUES_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      autocompleteValues: [],
    });
  });
});
