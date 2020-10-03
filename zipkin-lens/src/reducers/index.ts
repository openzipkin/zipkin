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
