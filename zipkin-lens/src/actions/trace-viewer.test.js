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
import * as actions from './trace-viewer-action';
import * as types from '../constants/action-types';

describe('trace viewer actions', () => {
  it('should create an action to set the trace', () => {
    const trace = {};
    const expectedAction = {
      type: types.TRACE_VIEWER__LOAD_TRACE,
      trace,
    };
    expect(actions.loadTrace(trace)).toEqual(expectedAction);
  });

  it('should create an action to set the error message', () => {
    const expectedAction = {
      type: types.TRACE_VIEWER__LOAD_TRACE_FAILURE,
      message: 'This is an error message',
    };
    expect(
      actions.loadTraceFailure('This is an error message'),
    ).toEqual(expectedAction);
  });
});
