/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { ThunkAction } from 'redux-thunk';

// Please see: https://github.com/reduxjs/redux-thunk/pull/278
declare module 'redux' {
  /*
   * Overload to add thunk support to Redux's dispatch() function.
   * Useful for react-redux or any other library which could use this type.
   */
  export interface Dispatch<A extends Action = AnyAction> {
    <TReturnType = any, TState = any, TExtraThunkArg = any>(
      thunkAction: ThunkAction<TReturnType, TState, TExtraThunkArg, A>,
    ): TReturnType;
  }
}
