import queryString from 'query-string';

import * as types from '../constants/action-types';
import * as api from '../constants/api';

export const fetchSpansRequest = () => ({
  type: types.FETCH_SPANS_REQUEST,
});

export const fetchSpansSuccess = spans => ({
  type: types.FETCH_SPANS_SUCCESS,
  spans,
});

export const fetchSpansFailure = () => ({
  type: types.FETCH_SPANS_FAILURE,
});

export const fetchSpans = serviceName => async (dispatch) => {
  dispatch(fetchSpansRequest());
  try {
    const query = queryString.stringify({ serviceName });
    const res = await fetch(`${api.SPANS}?${query}`);
    if (!res.ok) {
      throw Error(res.statusText);
    }
    const spans = await res.json();
    dispatch(fetchSpansSuccess(spans));
  } catch (err) {
    dispatch(fetchSpansFailure());
  }
};

export const clearSpans = () => ({
  type: types.CLEAR_SPANS,
});
