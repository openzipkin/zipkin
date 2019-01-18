import * as types from '../constants/action-types';
import * as api from '../constants/api';

export const fetchAutocompleteKeysRequest = () => ({
  type: types.FETCH_AUTOCOMPLETE_KEYS_REQUEST,
});

export const fetchAutocompleteKeysSuccess = autocompleteKeys => ({
  type: types.FETCH_AUTOCOMPLETE_KEYS_SUCCESS,
  autocompleteKeys,
});

export const fetchAutocompleteKeysFailure = () => ({
  type: types.FETCH_AUTOCOMPLETE_KEYS_FAILURE,
});

export const fetchAutocompleteKeys = () => async (dispatch) => {
  dispatch(fetchAutocompleteKeysRequest());
  try {
    const res = await fetch(api.AUTOCOMPLETE_KEYS);
    if (!res.ok) {
      throw Error(res.statusText);
    }
    const autocompleteKeys = await res.json();
    dispatch(fetchAutocompleteKeysSuccess(autocompleteKeys));
  } catch (err) {
    dispatch(fetchAutocompleteKeysFailure());
  }
};
