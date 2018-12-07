import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  trace: [],
};

const trace = (state = initialState, action) => {
  switch (action.type) {
    case types.FETCH_TRACE_REQUEST:
      return {
        ...state,
        isLoading: true,
        trace: [], /* Initialize trace */
      };
    case types.FETCH_TRACE_SUCCESS:
      return {
        ...state,
        isLoading: false,
        trace: action.trace,
      };
    case types.FETCH_TRACE_FAILURE:
      return {
        ...state,
        isLoading: false,
        trace: [],
      };
    default:
      return state;
  }
};

export default trace;
