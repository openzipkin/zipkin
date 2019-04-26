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

export const fetchTracesRequest = () => ({
  type: types.FETCH_TRACES_REQUEST,
});

export const fetchTracesSuccess = traces => ({
  type: types.FETCH_TRACES_SUCCESS,
  traces,
});

export const fetchTracesFailure = () => ({
  type: types.FETCH_TRACES_FAILURE,
});

const fetchTracesTimeout = 500;

export const fetchTraces = params => async (dispatch) => {
  dispatch(fetchTracesRequest());
  try {
    const query = queryString.stringify(params);

    /* Make the users feel loading time ... */
    const res = await Promise.all([
      fetch(`${api.TRACES}?${query}`),
      new Promise(resolve => setTimeout(resolve, fetchTracesTimeout)),
    ]);
    if (!res[0].ok) {
      throw Error(res[0].statusText);
    }
    const traces = await res[0].json();
    dispatch(fetchTracesSuccess(traces));
  } catch (err) {
    dispatch(fetchTracesFailure());
  }
};

export const clearTraces = () => ({
  type: types.CLEAR_TRACES,
});
