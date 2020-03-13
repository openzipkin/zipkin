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
import reducer from './trace';
import * as types from '../constants/action-types';

describe('trace reducer', () => {
  it('should handle the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      traceSummary: null,
    });
  });

  it('should handle TRACE_LOAD_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.TRACE_LOAD_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      traceSummary: null,
    });
  });

  it('should handle TRACE_LOAD_SUCCESS', () => {
    expect(
      reducer(
        {
          isLoading: true,
          traceSummary: {
            traceId: 'd050e0d52326cf81',
          },
        },
        {
          type: types.TRACE_LOAD_SUCCESS,
          traceSummary: {
            traceId: 'c020e0d52326cf84',
          },
        },
      ),
    ).toEqual({
      isLoading: false,
      traceSummary: {
        traceId: 'c020e0d52326cf84',
      },
    });
  });

  it('should handle TRACE_LOAD_FAILURE', () => {
    expect(
      reducer(
        {
          isLoading: true,
          traceSummary: {
            traceId: 'c020e0d52326cf84',
          },
        },
        {
          type: types.TRACE_LOAD_FAILURE,
        },
      ),
    ).toEqual({
      isLoading: false,
      traceSummary: null,
    });
  });
});
