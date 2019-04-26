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
import reducer from './remote-services';
import * as types from '../constants/action-types';

describe('remote services reducer', () => {
  it('should return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      remoteServices: [],
    });
  });

  it('should handle FETCH_REMOTE_SERVICES_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_REMOTE_SERVICES_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      remoteServices: [],
    });
  });

  it('should handle FETCH_REMOTE_SERVICES_SUCCESS', () => {
    expect(
      reducer({
        isLoading: true,
        remoteServices: ['remoteService1', 'remoteService2', 'remoteService3'],
      }, {
        type: types.FETCH_REMOTE_SERVICES_SUCCESS,
        remoteServices: ['remoteServiceA', 'remoteServiceB', 'remoteServiceC'],
      }),
    ).toEqual({
      isLoading: false,
      remoteServices: ['remoteServiceA', 'remoteServiceB', 'remoteServiceC'],
    });
  });

  it('should handle FETCH_REMOTE_SERVICES_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        remoteServices: ['remoteService1', 'remoteService2', 'remoteService3'],
      }, {
        type: types.FETCH_REMOTE_SERVICES_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      remoteServices: [],
    });
  });

  it('should handle CLEAR_REMOTE_SERVICES', () => {
    expect(
      reducer({
        isLoading: false,
        remoteServices: ['remoteService1', 'remoteService2', 'remoteService3'],
      }, {
        type: types.CLEAR_REMOTE_SERVICES,
      }),
    ).toEqual({
      isLoading: false,
      remoteServices: [],
    });
  });
});
