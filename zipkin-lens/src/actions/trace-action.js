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
import * as api from '../constants/api';

export const fetchTraceRequest = () => ({
  type: types.FETCH_TRACE_REQUEST,
});

export const fetchTraceSuccess = trace => ({
  type: types.FETCH_TRACE_SUCCESS,
  trace,
});

export const fetchTraceFailure = () => ({
  type: types.FETCH_TRACE_FAILURE,
});

const fetchTraceTimeout = 300;

export const fetchTrace = traceId => async (dispatch) => {
  dispatch(fetchTraceRequest());
  try {
    /* Make the users feel loading time ... */
    const res = await Promise.all([
      fetch(`${api.TRACE}/${traceId}`),
      new Promise(resolve => setTimeout(resolve, fetchTraceTimeout)),
    ]);
    if (!res[0].ok) {
      throw Error(res[0].statusText);
    }
    const trace = await res[0].json();
    dispatch(fetchTraceSuccess(trace));
  } catch (err) {
    dispatch(fetchTraceFailure());
  }
};
