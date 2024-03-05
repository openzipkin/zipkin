/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { combineReducers } from 'redux';

import appSlice from '../components/App/slice';
import autocompleteKeysSlice from '../slices/autocompleteKeysSlice';
import autocompleteValuesSlice from '../slices/autocompleteValuesSlice';
import dependenciesSlice from '../slices/dependenciesSlice';
import remoteServicesSlice from '../slices/remoteServicesSlice';
import servicesSlice from '../slices/servicesSlice';
import spansSlice from '../slices/spansSlice';
import tracesSlice from '../slices/tracesSlice';

const createReducer = () =>
  combineReducers({
    [appSlice.name]: appSlice.reducer,
    [autocompleteKeysSlice.name]: autocompleteKeysSlice.reducer,
    [autocompleteValuesSlice.name]: autocompleteValuesSlice.reducer,
    [dependenciesSlice.name]: dependenciesSlice.reducer,
    [remoteServicesSlice.name]: remoteServicesSlice.reducer,
    [servicesSlice.name]: servicesSlice.reducer,
    [spansSlice.name]: spansSlice.reducer,
    [tracesSlice.name]: tracesSlice.reducer,
  });

export default createReducer;
