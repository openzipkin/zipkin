import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  traces: [],
};

const traces = (state = initialState, action) => {
  switch (action.type) {
    case types.FETCH_TRACES_REQUEST:
      return {
        ...state,
        isLoading: true,
      };
    case types.FETCH_TRACES_SUCCESS:
      return {
        ...state,
        isLoading: false,
        traces: action.traces,
      };
    case types.FETCH_TRACES_FAILURE:
      return {
        ...state,
        isLoading: false,
        traces: [],
      };
    case types.CLEAR_TRACES:
      return {
        ...state,
        traces: [],
      };
    default:
      return state;
  }
};

export default traces;
