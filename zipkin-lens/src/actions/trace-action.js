import * as types from '../constants/action-types';
import * as api from '../constants/api';

export const fetchTraceRequest = () => ({
  type: types.FETCH_TRACE_REQUEST,
});

export const fetchTraceSuccess = trace => ({
  type: types.FETCH_TRACE_SUCCESS,
  trace,
});

export const fetchTraceFailure = () => ({
  type: types.FETCH_TRACE_FAILURE,
});

const fetchTraceTimeout = 300;

export const fetchTrace = traceId => async (dispatch) => {
  dispatch(fetchTraceRequest());
  try {
    /* Make the users feel loading time ... */
    const res = await Promise.all([
      fetch(`${api.TRACE}/${traceId}`),
      new Promise(resolve => setTimeout(resolve, fetchTraceTimeout)),
    ]);
    if (!res[0].ok) {
      throw Error(res[0].statusText);
    }
    const trace = await res[0].json();
    dispatch(fetchTraceSuccess(trace));
  } catch (err) {
    dispatch(fetchTraceFailure());
  }
};
