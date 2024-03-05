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

export const loadAutocompleteValues = createAsyncThunk(
  'autocompleteValues/fetch',
  async (autocompleteKey: string) => {
    const params = new URLSearchParams();
    params.set('key', autocompleteKey);
    const resp = await fetch(`${api.AUTOCOMPLETE_VALUES}?${params.toString()}`);
    if (!resp.ok) {
      throw Error(resp.statusText);
    }
    const json = await resp.json();
    return json as string[];
  },
);

export interface AutocompleteValuesState {
  isLoading: boolean;
  autocompleteValues: string[];
  error?: SerializedError;
}

const initialState: AutocompleteValuesState = {
  isLoading: false,
  autocompleteValues: [],
  error: undefined,
};

const autocompleteValuesSlice = createSlice({
  name: 'autocompleteValues',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(loadAutocompleteValues.pending, (state) => {
      state.isLoading = true;
      state.autocompleteValues = [];
      state.error = undefined;
    });
    builder.addCase(loadAutocompleteValues.fulfilled, (state, action) => {
      state.isLoading = false;
      state.autocompleteValues = action.payload;
      state.error = undefined;
    });
    builder.addCase(loadAutocompleteValues.rejected, (state, action) => {
      state.isLoading = false;
      state.autocompleteValues = [];
      state.error = action.error;
    });
  },
});

export default autocompleteValuesSlice;
