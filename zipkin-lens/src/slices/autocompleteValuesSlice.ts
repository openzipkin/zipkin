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
