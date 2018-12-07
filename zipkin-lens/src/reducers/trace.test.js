import reducer from './trace';
import * as types from '../constants/action-types';

describe('trace reducer', () => {
  it('should handle the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      trace: [],
    });
  });

  it('should handle FETCH_TRACE_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_TRACE_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      trace: [],
    });
  });

  it('should handle FETCH_TRACE_SUCCESS', () => {
    expect(
      reducer({
        isLoading: true,
        trace: [
          {
            traceId: 'd050e0d52326cf81',
            parentId: 'd050e0d52326cf81',
            id: 'd1ccbada31490783',
            kind: 'CLIENT',
            name: 'getInfoByAccessToken',
            timestamp: 1542337504412859,
            duration: 8667,
            localEndpoint: {
              serviceName: 'serviceA',
              ipv4: '127.0.0.1',
            },
            remoteEndpoint: {
              serviceName: 'serviceB',
              ipv4: '127.0.0.2',
              port: 8080,
            },
          },
        ],
      }, {
        type: types.FETCH_TRACE_SUCCESS,
        trace: [
          {
            traceId: 'c020e0d52326cf84',
            parentId: 'c020e0d52326cf84',
            id: 'd1fasdfda31490783',
            kind: 'CLIENT',
            name: 'getName',
            timestamp: 1542297504412859,
            duration: 86780,
            localEndpoint: {
              serviceName: 'serviceC',
              ipv4: '127.0.0.3',
            },
            remoteEndpoint: {
              serviceName: 'serviceD',
              ipv4: '127.0.0.4',
              port: 8080,
            },
          },
        ],
      }),
    ).toEqual({
      isLoading: false,
      trace: [
        {
          traceId: 'c020e0d52326cf84',
          parentId: 'c020e0d52326cf84',
          id: 'd1fasdfda31490783',
          kind: 'CLIENT',
          name: 'getName',
          timestamp: 1542297504412859,
          duration: 86780,
          localEndpoint: {
            serviceName: 'serviceC',
            ipv4: '127.0.0.3',
          },
          remoteEndpoint: {
            serviceName: 'serviceD',
            ipv4: '127.0.0.4',
            port: 8080,
          },
        },
      ],
    });
  });

  it('should handle FETCH_TRACE_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        trace: [
          {
            traceId: 'c020e0d52326cf84',
            parentId: 'c020e0d52326cf84',
            id: 'd1fasdfda31490783',
            kind: 'CLIENT',
            name: 'getName',
            timestamp: 1542297504412859,
            duration: 86780,
            localEndpoint: {
              serviceName: 'serviceC',
              ipv4: '127.0.0.3',
            },
            remoteEndpoint: {
              serviceName: 'serviceD',
              ipv4: '127.0.0.4',
              port: 8080,
            },
          },
        ],
      }, {
        type: types.FETCH_TRACE_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      trace: [],
    });
  });
});
