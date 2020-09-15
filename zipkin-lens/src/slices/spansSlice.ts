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

export const loadSpans = createAsyncThunk(
  'spans/fetch',
  async (serviceName: string) => {
    const params = new URLSearchParams();
    params.set('serviceName', serviceName);
    const resp = await fetch(`${api.SPANS}?${params.toString()}`);
    if (!resp.ok) {
      throw Error(resp.statusText);
    }
    const json = await resp.json();
    return json as string[];
  },
);

export interface SpansState {
  isLoading: boolean;
  spans: string[];
  error?: SerializedError;
}

const initialState: SpansState = {
  isLoading: false,
  spans: [],
  error: undefined,
};

const spansSlice = createSlice({
  name: 'spans',
  initialState,
  reducers: {}, // SearchBar.tsx issues load on serviceName change, so no need to clear
  extraReducers: (builder) => {
    builder.addCase(loadSpans.pending, (state) => {
      state.isLoading = true;
      state.spans = [];
      state.error = undefined;
    });
    builder.addCase(loadSpans.fulfilled, (state, action) => {
      state.isLoading = false;
      state.spans = action.payload;
      state.error = undefined;
    });
    builder.addCase(loadSpans.rejected, (state, action) => {
      state.isLoading = false;
      state.spans = [];
      state.error = action.error;
    });
  },
});

export default spansSlice;
