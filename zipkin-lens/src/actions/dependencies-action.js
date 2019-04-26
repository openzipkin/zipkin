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

export const fetchDependenciesRequest = () => ({
  type: types.FETCH_DEPENDENCIES_REQUEST,
});

export const fetchDependenciesSuccess = dependencies => ({
  type: types.FETCH_DEPENDENCIES_SUCCESS,
  dependencies,
});

export const fetchDependenciesFailure = () => ({
  type: types.FETCH_DEPENDENCIES_FAILURE,
});

const fetchDependenciesTimeout = 500;

export const fetchDependencies = params => async (dispatch) => {
  dispatch(fetchDependenciesRequest());
  try {
    const query = queryString.stringify(params);

    const res = await Promise.all([
      fetch(`${api.DEPENDENCIES}?${query}`),
      new Promise(resolve => setTimeout(resolve, fetchDependenciesTimeout)),
    ]);
    if (!res[0].ok) {
      throw Error(res[0].statusText);
    }
    const dependencies = await res[0].json();
    dispatch(fetchDependenciesSuccess(dependencies));
  } catch (err) {
    dispatch(fetchDependenciesFailure());
  }
};

export const clearDependencies = () => ({
  type: types.CLEAR_DEPENDENCIES,
});
