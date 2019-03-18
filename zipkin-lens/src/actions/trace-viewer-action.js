import * as types from '../constants/action-types';

export const setTrace = trace => ({
  type: types.TRACE_VIEWER_SET_TRACE,
  trace,
});
