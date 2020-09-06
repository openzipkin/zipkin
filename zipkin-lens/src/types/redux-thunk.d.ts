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
