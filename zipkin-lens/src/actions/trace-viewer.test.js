import * as actions from './trace-viewer-action';
import * as types from '../constants/action-types';

describe('trace viewer actions', () => {
  it('should create an action to set the trace', () => {
    const trace = {};
    const expectedAction = {
      type: types.TRACE_VIEWER__LOAD_TRACE,
      trace,
    };
    expect(actions.setTrace(trace)).toEqual(expectedAction);
  });
});
