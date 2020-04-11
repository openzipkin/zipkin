/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import { DependenciesAction, ActionTypes } from '../types/action-types';
import Dependencies from '../types/Dependencies';
import AppThunk from '../types/AppThunk';
import * as api from '../constants/api';

export const loadDependenciesRequest = (): DependenciesAction => ({
  type: ActionTypes.LOAD_DEPENDENCIES_REQUEST,
});

export const loadDependenciesSuccess = (
  dependencies: Dependencies,
): DependenciesAction => ({
  type: ActionTypes.LOAD_DEPENDENCIES_SUCCESS,
  payload: {
    dependencies,
  },
});

export const loadDependenciesFailure = (error: any): DependenciesAction => ({
  type: ActionTypes.LOAD_DEPENDENCIES_FAILURE,
  payload: {
    error,
  },
});

export const loadDependencies = (params: {
  lookback?: number;
  endTs: number;
}): AppThunk => async (dispatch) => {
  dispatch(loadDependenciesRequest());

  try {
    const ps = new URLSearchParams();
    if (params.lookback) {
      ps.set('lookback', params.lookback.toString());
    }
    ps.set('endTs', params.endTs.toString());

    const res = await Promise.all<Response, any>([
      fetch(`${api.DEPENDENCIES}?${ps.toString()}`),
      new Promise((resolve) => setTimeout(resolve, 500)),
    ]);

    if (!res[0].ok) {
      throw Error(res[0].statusText);
    }
    const dependencies = await res[0].json();
    dispatch(loadDependenciesSuccess(dependencies));
  } catch (err) {
    dispatch(loadDependenciesFailure(err));
  }
};

export const clearDependencies = (): DependenciesAction => ({
  type: ActionTypes.CLEAR_DEPENDENCIES,
});
