import reducer from './remote-services';
import * as types from '../constants/action-types';

describe('remote services reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      remoteServices: [],
    });
  });

  it('should handle FETCH_REMOTE_SERVICES_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_REMOTE_SERVICES_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      remoteServices: [],
    });
  });

  it('should handle FETCH_REMOTE_SERVICES_SUCCESS', () => {
    expect(
      reducer({
        isLoading: true,
        remoteServices: ['remoteService1', 'remoteService2', 'remoteService3'],
      }, {
        type: types.FETCH_REMOTE_SERVICES_SUCCESS,
        remoteServices: ['remoteServiceA', 'remoteServiceB', 'remoteServiceC'],
      }),
    ).toEqual({
      isLoading: false,
      remoteServices: ['remoteServiceA', 'remoteServiceB', 'remoteServiceC'],
    });
  });

  it('should handle FETCH_REMOTE_SERVICES_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        remoteServices: ['remoteService1', 'remoteService2', 'remoteService3'],
      }, {
        type: types.FETCH_REMOTE_SERVICES_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      remoteServices: [],
    });
  });

  it('should handle CLEAR_REMOTE_SERVICES', () => {
    expect(
      reducer({
        isLoading: false,
        remoteServices: ['remoteService1', 'remoteService2', 'remoteService3'],
      }, {
        type: types.CLEAR_REMOTE_SERVICES,
      }),
    ).toEqual({
      isLoading: false,
      remoteServices: [],
    });
  });
});
