import * as types from '../constants/action-types';

const initialState = {
  trace: null,
};

const traceViewer = (state = initialState, action) => {
  switch (action.type) {
    case types.TRACE_VIEWER__LOAD_TRACE:
      return {
        ...state,
        trace: action.trace,
      };
    default:
      return state;
  }
};

export default traceViewer;
