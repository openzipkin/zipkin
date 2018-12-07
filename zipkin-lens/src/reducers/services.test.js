import reducer from './services';
import * as types from '../constants/action-types';

describe('services reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      services: [],
    });
  });

  it('should handle FETCH_SERVICES_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_SERVICES_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      services: [],
    });
  });

  it('should handle FETCH_SERVICES_SUCCESS', () => {
    expect(
      reducer({
        isLoading: true,
        services: ['service1', 'service2', 'service3'],
      }, {
        type: types.FETCH_SERVICES_SUCCESS,
        services: ['serviceA', 'serviceB', 'serviceC'],
      }),
    ).toEqual({
      isLoading: false,
      services: ['serviceA', 'serviceB', 'serviceC'],
    });
  });

  it('should handle FETCH_SERVICES_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        services: ['service1', 'service2', 'service3'],
      }, {
        type: types.FETCH_SERVICES_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      services: [],
    });
  });
});
