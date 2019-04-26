/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
