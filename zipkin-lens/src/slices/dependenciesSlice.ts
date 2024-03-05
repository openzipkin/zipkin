/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/* eslint-disable no-param-reassign */

import {
  SerializedError,
  createAsyncThunk,
  createSlice,
} from '@reduxjs/toolkit';

import * as api from '../constants/api';
import Dependencies from '../models/Dependencies';

export const loadDependencies = createAsyncThunk(
  'dependencies/fetch',
  async (params: { lookback?: number; endTs: number }) => {
    const ps = new URLSearchParams();
    if (params.lookback) {
      ps.set('lookback', params.lookback.toString());
    }
    ps.set('endTs', params.endTs.toString());

    const resp = await fetch(`${api.DEPENDENCIES}?${ps.toString()}`);
    if (!resp.ok) {
      throw Error(resp.statusText);
    }
    const json = await resp.json();
    return json as Dependencies;
  },
);

export interface DependenciesState {
  isLoading: boolean;
  dependencies: Dependencies;
  error?: SerializedError;
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
    clearDependencies: (state) => {
      state.isLoading = false;
      state.dependencies = [];
      state.error = undefined;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(loadDependencies.pending, (state) => {
      state.isLoading = true;
      state.dependencies = [];
      state.error = undefined;
    });
    builder.addCase(loadDependencies.fulfilled, (state, action) => {
      state.isLoading = false;
      state.dependencies = action.payload;
      state.error = undefined;
    });
    builder.addCase(loadDependencies.rejected, (state, action) => {
      state.isLoading = false;
      state.dependencies = [];
      state.error = action.error;
    });
  },
});

export const { clearDependencies } = dependenciesSlice.actions;

export default dependenciesSlice;
