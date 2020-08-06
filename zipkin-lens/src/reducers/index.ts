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
import dependenciesSlice from '../slices/dependenciesSlice';

import remoteServices from './remote-services';
import spans from './spans';
import trace from './trace';
import traces from './traces';
import services from './services';
import autocompleteKeys from './autocomplete-keys';
import autocompleteValues from './autocomplete-values';
import traceViewer from './trace-viewer';

const createReducer = () =>
  combineReducers({
    [appSlice.name]: appSlice.reducer,
    [dependenciesSlice.name]: dependenciesSlice.reducer,
    remoteServices,
    spans,
    trace,
    traces,
    services,
    autocompleteKeys,
    autocompleteValues,
    traceViewer,
  });

export default createReducer;
