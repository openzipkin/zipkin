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
import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  autocompleteKeys: [],
};

const autocompleteKeys = (state = initialState, action) => {
  switch (action.type) {
    case types.FETCH_AUTOCOMPLETE_KEYS_REQUEST:
      return {
        ...state,
        isLoading: true,
      };
    case types.FETCH_AUTOCOMPLETE_KEYS_SUCCESS:
      return {
        ...state,
        isLoading: false,
        autocompleteKeys: action.autocompleteKeys,
      };
    case types.FETCH_AUTOCOMPLETE_KEYS_FAILURE:
      return {
        ...state,
        isLoading: false,
        autocompleteKeys: [],
      };
    default:
      return state;
  }
};

export default autocompleteKeys;
