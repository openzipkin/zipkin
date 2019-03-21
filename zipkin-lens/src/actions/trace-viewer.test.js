import * as actions from './trace-viewer-action';
import * as types from '../constants/action-types';

describe('trace viewer actions', () => {
  it('should create an action to set the trace', () => {
    const trace = {};
    const expectedAction = {
      type: types.TRACE_VIEWER__LOAD_TRACE,
      trace,
    };
    expect(actions.loadTrace(trace)).toEqual(expectedAction);
  });

  it('should create an action to set the error message', () => {
    const expectedAction = {
      type: types.TRACE_VIEWER__LOAD_TRACE_FAILURE,
      message: 'This is an error message',
    };
    expect(
      actions.loadTraceFailure('This is an error message'),
    ).toEqual(expectedAction);
  });
});
