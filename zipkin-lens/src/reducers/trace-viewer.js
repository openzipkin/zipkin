import * as types from '../constants/action-types';

const initialState = {
  trace: null,
  isMalformedFile: false,
  errorMessage: '',
};

const traceViewer = (state = initialState, action) => {
  switch (action.type) {
    case types.TRACE_VIEWER__LOAD_TRACE:
      return {
        ...state,
        trace: action.trace,
        isMalformedFile: false,
        errorMessage: '',
      };
    case types.TRACE_VIEWER__LOAD_TRACE_FAILURE:
      return {
        ...state,
        trace: null,
        isMalformedFile: true,
        errorMessage: action.message,
      };
    default:
      return state;
  }
};

export default traceViewer;
