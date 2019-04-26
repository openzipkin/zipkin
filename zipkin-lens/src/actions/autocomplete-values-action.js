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
import queryString from 'query-string';

import * as types from '../constants/action-types';
import * as api from '../constants/api';

export const fetchAutocompleteValuesRequest = () => ({
  type: types.FETCH_AUTOCOMPLETE_VALUES_REQUEST,
});

export const fetchAutocompleteValuesSuccess = autocompleteValues => ({
  type: types.FETCH_AUTOCOMPLETE_VALUES_SUCCESS,
  autocompleteValues,
});

export const fetchAutocompleteValuesFailure = () => ({
  type: types.FETCH_AUTOCOMPLETE_VALUES_FAILURE,
});

export const fetchAutocompleteValues = autocompleteKey => async (dispatch) => {
  dispatch(fetchAutocompleteValuesRequest());
  try {
    const query = queryString.stringify({ key: autocompleteKey });
    const res = await fetch(`${api.AUTOCOMPLETE_VALUES}?${query}`);
    if (!res.ok) {
      throw Error(res.statusText);
    }
    const autocompleteValues = await res.json();
    dispatch(fetchAutocompleteValuesSuccess(autocompleteValues));
  } catch (err) {
    dispatch(fetchAutocompleteValuesFailure());
  }
};
