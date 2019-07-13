/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import * as types from '../constants/action-types';

const initialState = {
  isLoading: false,
  isCalculating: false,
  traces: [],
  traceSummaries: [],
  correctedTraceMap: {},
};

const traces = (state = initialState, action) => {
  switch (action.type) {
    case types.TRACES_LOAD_REQUEST:
      return {
        ...state,
        isLoading: true,
      };
    case types.TRACES_LOAD_SUCCESS:
      return {
        ...state,
        isLoading: false,
        traces: action.traces,
        traceSummaries: action.traceSummaries,
        correctedTraceMap: action.correctedTraceMap,
      };
    case types.TRACES_LOAD_FAILURE:
      return {
        ...state,
        isLoading: false,
        traces: [],
        traceSummaries: [],
        correctedTraceMap: {},
      };
    case types.TRACES_CLEAR:
      return {
        ...state,
        isLoading: false,
        traces: [],
        traceSummaries: [],
        correctedTraceMap: {},
      };
    default:
      return state;
  }
};

export default traces;
