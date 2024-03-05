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

export const loadRemoteServices = createAsyncThunk(
  'remoteServices/fetch',
  async (serviceName: string) => {
    const params = new URLSearchParams();
    params.set('serviceName', serviceName);
    const resp = await fetch(`${api.REMOTE_SERVICES}?${params.toString()}`);
    if (!resp.ok) {
      throw Error(resp.statusText);
    }
    const json = await resp.json();
    return json as string[];
  },
);

export interface RemoteServicesState {
  isLoading: boolean;
  remoteServices: string[];
  error?: SerializedError;
}

const initialState: RemoteServicesState = {
  isLoading: false,
  remoteServices: [],
  error: undefined,
};

const remoteServicesSlice = createSlice({
  name: 'remoteServices',
  initialState,
  reducers: {}, // SearchBar.tsx issues load on serviceName change, so no need to clear
  extraReducers: (builder) => {
    builder.addCase(loadRemoteServices.pending, (state) => {
      state.isLoading = true;
      state.remoteServices = [];
      state.error = undefined;
    });
    builder.addCase(loadRemoteServices.fulfilled, (state, action) => {
      state.isLoading = false;
      state.remoteServices = action.payload;
      state.error = undefined;
    });
    builder.addCase(loadRemoteServices.rejected, (state, action) => {
      state.isLoading = false;
      state.remoteServices = [];
      state.error = action.error;
    });
  },
});

export default remoteServicesSlice;
