import reducer from './dependencies';
import * as types from '../constants/action-types';

describe('dependencies reducer', () => {
  it('shoud return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      dependencies: [],
    });
  });

  it('should handle FETCH_DEPENDENCIES_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_DEPENDENCIES_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      dependencies: [],
    });
  });

  it('should handle FETCH_DEPENDENCIES_SUCCESS', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_DEPENDENCIES_SUCCESS,
        dependencies: [
          {
            parent: 'service1',
            child: 'service2',
            callCount: 100,
            errorCount: 5,
          },
          {
            parent: 'service3',
            child: 'service2',
            callCount: 4,
          },
        ],
      }),
    ).toEqual({
      isLoading: false,
      dependencies: [
        {
          parent: 'service1',
          child: 'service2',
          callCount: 100,
          errorCount: 5,
        },
        {
          parent: 'service3',
          child: 'service2',
          callCount: 4,
        },
      ],
    });

    expect(
      reducer({
        isLoading: true,
        dependencies: [
          {
            parent: 'serviceA',
            child: 'serviceB',
            callCount: 4,
            errorCount: 1,
          },
        ],
      }, {
        type: types.FETCH_DEPENDENCIES_SUCCESS,
        dependencies: [
          {
            parent: 'service1',
            child: 'service2',
            callCount: 100,
            errorCount: 5,
          },
          {
            parent: 'service3',
            child: 'service2',
            callCount: 4,
          },
        ],
      }),
    ).toEqual({
      isLoading: false,
      dependencies: [
        {
          parent: 'service1',
          child: 'service2',
          callCount: 100,
          errorCount: 5,
        },
        {
          parent: 'service3',
          child: 'service2',
          callCount: 4,
        },
      ],
    });
  });

  it('should handle FETCH_DEPENDENCIES_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        dependencies: [
          {
            parent: 'service1',
            child: 'service2',
            callCount: 100,
            errorCount: 5,
          },
          {
            parent: 'service3',
            child: 'service2',
            callCount: 4,
          },
        ],
      }, {
        type: types.FETCH_DEPENDENCIES_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      dependencies: [],
    });
  });

  it('should handle CLEAN_DEPENDENCIES', () => {
    expect(
      reducer({
        isLoading: true,
        dependencies: [
          {
            parent: 'service1',
            child: 'service2',
            callCount: 100,
            errorCount: 5,
          },
          {
            parent: 'service3',
            child: 'service2',
            callCount: 4,
          },
        ],
      }, {
        type: types.CLEAR_DEPENDENCIES,
      }),
    ).toEqual({
      isLoading: true,
      dependencies: [],
    });
  });
});
