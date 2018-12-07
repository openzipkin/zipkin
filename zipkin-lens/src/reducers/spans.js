import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  spans: [],
};

const spans = (state = initialState, action) => {
  switch (action.type) {
    case types.FETCH_SPANS_REQUEST:
      return {
        ...state,
        isLoading: true,
        spans: [], /* Initialize spans */
      };
    case types.FETCH_SPANS_SUCCESS:
      return {
        ...state,
        isLoading: false,
        spans: action.spans,
      };
    case types.FETCH_SPANS_FAILURE:
      return {
        ...state,
        isLoading: false,
        spans: [],
      };
    case types.CLEAR_SPANS:
      return {
        ...state,
        spans: [],
      };
    default:
      return state;
  }
};

export default spans;
