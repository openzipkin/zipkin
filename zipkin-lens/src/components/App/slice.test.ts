/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect } from 'vitest';
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
