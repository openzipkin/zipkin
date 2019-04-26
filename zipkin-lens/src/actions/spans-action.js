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

export const fetchSpansRequest = () => ({
  type: types.FETCH_SPANS_REQUEST,
});

export const fetchSpansSuccess = spans => ({
  type: types.FETCH_SPANS_SUCCESS,
  spans,
});

export const fetchSpansFailure = () => ({
  type: types.FETCH_SPANS_FAILURE,
});

export const fetchSpans = serviceName => async (dispatch) => {
  dispatch(fetchSpansRequest());
  try {
    const query = queryString.stringify({ serviceName });
    const res = await fetch(`${api.SPANS}?${query}`);
    if (!res.ok) {
      throw Error(res.statusText);
    }
    const spans = await res.json();
    dispatch(fetchSpansSuccess(spans));
  } catch (err) {
    dispatch(fetchSpansFailure());
  }
};

export const clearSpans = () => ({
  type: types.CLEAR_SPANS,
});
