/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { createStore, applyMiddleware } from 'redux';
import thunk from 'redux-thunk';

import createReducer from '../reducers';

export default function configureStore(config) {
  return createStore(createReducer(config), applyMiddleware(thunk));
}
