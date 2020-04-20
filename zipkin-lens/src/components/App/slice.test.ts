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

import slice, { clearAlert, setAlert } from './slice';

describe('<App /> slice', () => {
  const { reducer } = slice;
  const alert = {
    message: 'Hi there!',
    severity: 'success' as const,
  };

  it('initially has alert closed', () => {
    expect(reducer(undefined, { type: 'undefined' }).alertOpen).toEqual(false);
  });

  it('sets alert when setAlert called', () => {
    const state = reducer(undefined, setAlert(alert));
    expect(state.alertOpen).toEqual(true);
    expect(state.alert).toEqual(alert);
  });

  it('alert not open when clearAlert called', () => {
    const alerted = reducer(undefined, setAlert(alert));
    const state = reducer(alerted, clearAlert());
    expect(state.alertOpen).toEqual(false);
    // Alert still present in state to allow it to animate away.
    expect(state.alert).toEqual(alert);
  });
});
