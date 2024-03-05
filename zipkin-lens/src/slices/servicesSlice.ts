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

export const loadServices = createAsyncThunk('services/fetch', async () => {
  const resp = await fetch(api.SERVICES);
  if (!resp.ok) {
    throw Error(resp.statusText);
  }
  const json = await resp.json();
  return json as string[];
});

export interface ServicesState {
  isLoading: boolean;
  services: string[];
  error?: SerializedError;
}

const initialState: ServicesState = {
  isLoading: false,
  services: [],
  error: undefined,
};

const servicesSlice = createSlice({
  name: 'services',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(loadServices.pending, (state) => {
      state.isLoading = true;
      state.services = [];
      state.error = undefined;
    });
    builder.addCase(loadServices.fulfilled, (state, action) => {
      state.isLoading = false;
      state.services = action.payload;
      state.error = undefined;
    });
    builder.addCase(loadServices.rejected, (state, action) => {
      state.isLoading = false;
      state.services = [];
      state.error = action.error;
    });
  },
});

export default servicesSlice;
