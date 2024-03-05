/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import createReducer from '../reducers';

export type RootState = ReturnType<ReturnType<typeof createReducer>>;
