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
