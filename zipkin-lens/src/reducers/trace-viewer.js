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
  trace: null,
  isMalformedFile: false,
  errorMessage: '',
};

const traceViewer = (state = initialState, action) => {
  switch (action.type) {
    case types.TRACE_VIEWER__LOAD_TRACE:
      return {
        ...state,
        trace: action.trace,
        isMalformedFile: false,
        errorMessage: '',
      };
    case types.TRACE_VIEWER__LOAD_TRACE_FAILURE:
      return {
        ...state,
        trace: null,
        isMalformedFile: true,
        errorMessage: action.message,
      };
    default:
      return state;
  }
};

export default traceViewer;
