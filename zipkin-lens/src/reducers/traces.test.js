/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import reducer, { initialState } from './traces';
import * as types from '../constants/action-types';

describe('traces reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      traces: [],
      traceSummaries: [],
      correctedTraceMap: {},
      lastQueryParams: {},
    });
  });

  it('should handle TRACES_LOAD_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.TRACES_LOAD_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      traces: [],
      traceSummaries: [],
      correctedTraceMap: {},
      lastQueryParams: {},
    });
  });

  it('should handle TRACES_LOAD_SUCCESS', () => {
    expect(
      reducer(
        {
          isLoading: true,
          traces: [
            [
              {
                traceId: 'd050e0d52326cf81', // Omit details
              },
            ],
          ],
          traceSummaries: [
            {
              traceId: 'd050e0d52326cf81', // Omit details
            },
          ],
          correctedTraceMap: {
            d050e0d52326cf81: {}, // Omit details
          },
          lastQueryParams: {
            serviceName: 'serviceA',
          },
        },
        {
          type: types.TRACES_LOAD_SUCCESS,
          traces: [
            [
              {
                traceId: 'c020e0d52326cf84', // Omit details
              },
            ],
          ],
          traceSummaries: [
            {
              traceId: 'c020e0d52326cf84', // Omit details
            },
          ],
          correctedTraceMap: {
            c020e0d52326cf84: {}, // Omit details
          },
          lastQueryParams: {
            serviceName: 'serviceB',
          },
        },
      ),
    ).toEqual({
      isLoading: false,
      traces: [
        [
          {
            traceId: 'c020e0d52326cf84',
          },
        ],
      ],
      traceSummaries: [
        {
          traceId: 'c020e0d52326cf84',
        },
      ],
      correctedTraceMap: {
        c020e0d52326cf84: {},
      },
      lastQueryParams: {
        serviceName: 'serviceB',
      },
    });
  });

  it('should handle TRACES_LOAD_FAILURE', () => {
    expect(
      reducer(
        {
          isLoading: true,
          traces: [
            [
              {
                traceId: 'c020e0d52326cf84',
              },
            ],
          ],
        },
        {
          type: types.TRACES_LOAD_FAILURE,
        },
      ),
    ).toEqual({
      isLoading: false,
      traces: [],
      traceSummaries: [],
      correctedTraceMap: {},
      lastQueryParams: {},
    });
  });

  it('should handle CLEAR_TRACES', () => {
    expect(
      reducer(
        {
          isLoading: true,
          traces: [
            [
              {
                traceId: 'c020e0d52326cf84',
              },
            ],
          ],
        },
        {
          type: types.CLEAR_TRACES,
        },
      ),
    ).toEqual(initialState);
  });
});
