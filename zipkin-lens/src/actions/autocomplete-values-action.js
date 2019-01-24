import queryString from 'query-string';

import * as types from '../constants/action-types';
import * as api from '../constants/api';

export const fetchAutocompleteValuesRequest = () => ({
  type: types.FETCH_AUTOCOMPLETE_VALUES_REQUEST,
});

export const fetchAutocompleteValuesSuccess = autocompleteValues => ({
  type: types.FETCH_AUTOCOMPLETE_VALUES_SUCCESS,
  autocompleteValues,
});

export const fetchAutocompleteValuesFailure = () => ({
  type: types.FETCH_AUTOCOMPLETE_VALUES_FAILURE,
});

export const fetchAutocompleteValues = autocompleteKey => async (dispatch) => {
  dispatch(fetchAutocompleteValuesRequest());
  try {
    const query = queryString.stringify({ key: autocompleteKey });
    const res = await fetch(`${api.AUTOCOMPLETE_VALUES}?${query}`);
    if (!res.ok) {
      throw Error(res.statusText);
    }
    const autocompleteValues = await res.json();
    dispatch(fetchAutocompleteValuesSuccess(autocompleteValues));
  } catch (err) {
    dispatch(fetchAutocompleteValuesFailure());
  }
};
