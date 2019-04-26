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
import reducer from './autocomplete-values';
import * as types from '../constants/action-types';

describe('autocomplete-values reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      autocompleteValues: [],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_VALUES_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_AUTOCOMPLETE_VALUES_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      autocompleteValues: [],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_VALUES_SUCCESS', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_AUTOCOMPLETE_VALUES_SUCCESS,
        autocompleteValues: ['alpha', 'beta', 'release'],
      }),
    ).toEqual({
      isLoading: false,
      autocompleteValues: ['alpha', 'beta', 'release'],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_VALUES_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        autocompleteValues: ['alpha', 'beta', 'release'],
      }, {
        type: types.FETCH_AUTOCOMPLETE_VALUES_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      autocompleteValues: [],
    });
  });
});
