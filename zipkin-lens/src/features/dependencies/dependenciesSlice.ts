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

/* eslint-disable no-param-reassign */

import { PayloadAction, createSlice } from '@reduxjs/toolkit';

import Dependencies from './Dependencies';

export interface DependenciesState {
  isLoading: boolean;
  dependencies: Dependencies;
  error?: Error;
}

const initialState: DependenciesState = {
  isLoading: false,
  dependencies: [],
  error: undefined,
};

const dependenciesSlice = createSlice({
  name: 'dependencies',
  initialState,
  reducers: {
    loadDependenciesRequest: (state) => {
      state.isLoading = true;
      state.dependencies = [];
      state.error = undefined;
    },
    loadDependenciesSuccess: (state, action: PayloadAction<Dependencies>) => {
      state.isLoading = false;
      state.dependencies = action.payload;
      state.error = undefined;
    },
    loadDependenciesFailure: (state, action: PayloadAction<Error>) => {
      state.isLoading = false;
      state.dependencies = [];
      state.error = action.payload;
    },
    clearDependencies: (state) => {
      state.isLoading = false;
      state.dependencies = [];
      state.error = undefined;
    },
  },
});

export default dependenciesSlice;

export const {
  loadDependenciesRequest,
  loadDependenciesSuccess,
  loadDependenciesFailure,
  clearDependencies,
} = dependenciesSlice.actions;
