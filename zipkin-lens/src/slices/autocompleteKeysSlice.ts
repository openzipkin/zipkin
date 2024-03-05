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

export const loadAutocompleteKeys = createAsyncThunk(
  'autocompleteKeys/fetch',
  async () => {
    const resp = await fetch(api.AUTOCOMPLETE_KEYS);
    if (!resp.ok) {
      throw Error(resp.statusText);
    }
    const json = await resp.json();
    return json as string[];
  },
);

export interface AutocompleteKeysState {
  isLoading: boolean;
  autocompleteKeys: string[];
  error?: SerializedError;
}

const initialState: AutocompleteKeysState = {
  isLoading: false,
  autocompleteKeys: [],
  error: undefined,
};

const autocompleteKeysSlice = createSlice({
  name: 'autocompleteKeys',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(loadAutocompleteKeys.pending, (state) => {
      state.isLoading = true;
      state.autocompleteKeys = [];
      state.error = undefined;
    });
    builder.addCase(loadAutocompleteKeys.fulfilled, (state, action) => {
      state.isLoading = false;
      state.autocompleteKeys = action.payload;
      state.error = undefined;
    });
    builder.addCase(loadAutocompleteKeys.rejected, (state, action) => {
      state.isLoading = false;
      state.autocompleteKeys = [];
      state.error = action.error;
    });
  },
});

export default autocompleteKeysSlice;
