import * as types from '../constants/action-types';

export const loadTrace = trace => ({
  type: types.TRACE_VIEWER__LOAD_TRACE,
  trace,
});
