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
import reducer from './dependencies';
import * as types from '../constants/action-types';

describe('dependencies reducer', () => {
  it('shoud return the initial state', () => {
    expect(reducer(undefined, {})).toEqual({
      isLoading: false,
      dependencies: [],
    });
  });

  it('should handle FETCH_DEPENDENCIES_REQUEST', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_DEPENDENCIES_REQUEST,
      }),
    ).toEqual({
      isLoading: true,
      dependencies: [],
    });
  });

  it('should handle FETCH_DEPENDENCIES_SUCCESS', () => {
    expect(
      reducer(undefined, {
        type: types.FETCH_DEPENDENCIES_SUCCESS,
        dependencies: [
          {
            parent: 'service1',
            child: 'service2',
            callCount: 100,
            errorCount: 5,
          },
          {
            parent: 'service3',
            child: 'service2',
            callCount: 4,
          },
        ],
      }),
    ).toEqual({
      isLoading: false,
      dependencies: [
        {
          parent: 'service1',
          child: 'service2',
          callCount: 100,
          errorCount: 5,
        },
        {
          parent: 'service3',
          child: 'service2',
          callCount: 4,
        },
      ],
    });

    expect(
      reducer({
        isLoading: true,
        dependencies: [
          {
            parent: 'serviceA',
            child: 'serviceB',
            callCount: 4,
            errorCount: 1,
          },
        ],
      }, {
        type: types.FETCH_DEPENDENCIES_SUCCESS,
        dependencies: [
          {
            parent: 'service1',
            child: 'service2',
            callCount: 100,
            errorCount: 5,
          },
          {
            parent: 'service3',
            child: 'service2',
            callCount: 4,
          },
        ],
      }),
    ).toEqual({
      isLoading: false,
      dependencies: [
        {
          parent: 'service1',
          child: 'service2',
          callCount: 100,
          errorCount: 5,
        },
        {
          parent: 'service3',
          child: 'service2',
          callCount: 4,
        },
      ],
    });
  });

  it('should handle FETCH_DEPENDENCIES_FAILURE', () => {
    expect(
      reducer({
        isLoading: true,
        dependencies: [
          {
            parent: 'service1',
            child: 'service2',
            callCount: 100,
            errorCount: 5,
          },
          {
            parent: 'service3',
            child: 'service2',
            callCount: 4,
          },
        ],
      }, {
        type: types.FETCH_DEPENDENCIES_FAILURE,
      }),
    ).toEqual({
      isLoading: false,
      dependencies: [],
    });
  });

  it('should handle CLEAN_DEPENDENCIES', () => {
    expect(
      reducer({
        isLoading: true,
        dependencies: [
          {
            parent: 'service1',
            child: 'service2',
            callCount: 100,
            errorCount: 5,
          },
          {
            parent: 'service3',
            child: 'service2',
            callCount: 4,
          },
        ],
      }, {
        type: types.CLEAR_DEPENDENCIES,
      }),
    ).toEqual({
      isLoading: true,
      dependencies: [],
    });
  });
});
