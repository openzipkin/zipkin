import reducer from './spans';
import * as types from '../constants/action-types';

describe('spans reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      spans: [],
    });
  });

  it('should handle FETCH_SPANS_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_SPANS_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      spans: [],
    });
  });

  it('should handle FETCH_SPANS_SUCCESS', () => {
    expect(
      reducer({
        isLoading: true,
        spans: ['span1', 'span2', 'span3'],
      }, {
        type: types.FETCH_SPANS_SUCCESS,
        spans: ['spanA', 'spanB', 'spanC'],
      }),
    ).toEqual({
      isLoading: false,
      spans: ['spanA', 'spanB', 'spanC'],
    });
  });

  it('should handle FETCH_SPANS_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        spans: ['span1', 'span2', 'span3'],
      }, {
        type: types.FETCH_SPANS_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      spans: [],
    });
  });

  it('should handle CLEAR_SPANS', () => {
    expect(
      reducer({
        isLoading: false,
        spans: ['span1', 'span2', 'span3'],
      }, {
        type: types.CLEAR_SPANS,
      }),
    ).toEqual({
      isLoading: false,
      spans: [],
    });
  });
});
