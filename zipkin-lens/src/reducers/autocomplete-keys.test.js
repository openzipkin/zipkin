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
import reducer from './autocomplete-keys';
import * as types from '../constants/action-types';

describe('autocomplete-keys reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      autocompleteKeys: [],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_KEYS_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_AUTOCOMPLETE_KEYS_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      autocompleteKeys: [],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_KEYS_SUCCESS', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_AUTOCOMPLETE_KEYS_SUCCESS,
        autocompleteKeys: ['environment', 'phase'],
      }),
    ).toEqual({
      isLoading: false,
      autocompleteKeys: ['environment', 'phase'],
    });
  });

  it('should handle FETCH_AUTOCOMPLETE_KEYS_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        autocompleteKeys: ['environment', 'phase'],
      }, {
        type: types.FETCH_AUTOCOMPLETE_KEYS_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      autocompleteKeys: [],
    });
  });
});
