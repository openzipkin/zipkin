import queryString from 'query-string';

import * as types from '../constants/action-types';
import * as api from '../constants/api';

export const fetchTracesRequest = () => ({
  type: types.FETCH_TRACES_REQUEST,
});

export const fetchTracesSuccess = traces => ({
  type: types.FETCH_TRACES_SUCCESS,
  traces,
});

export const fetchTracesFailure = () => ({
  type: types.FETCH_TRACES_FAILURE,
});

const fetchTracesTimeout = 500;

export const fetchTraces = params => async (dispatch) => {
  dispatch(fetchTracesRequest());
  try {
    const query = queryString.stringify(params);

    /* Make the users feel loading time ... */
    const res = await Promise.all([
      fetch(`${api.TRACES}?${query}`),
      new Promise(resolve => setTimeout(resolve, fetchTracesTimeout)),
    ]);
    if (!res[0].ok) {
      throw Error(res[0].statusText);
    }
    const traces = await res[0].json();
    dispatch(fetchTracesSuccess(traces));
  } catch (err) {
    dispatch(fetchTracesFailure());
  }
};

export const clearTraces = () => ({
  type: types.CLEAR_TRACES,
});
