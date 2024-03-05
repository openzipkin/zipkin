/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Color } from '@material-ui/lab/Alert';
import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface Alert {
  message: string;
  severity: Color;
}

interface AppState {
  alert: Alert;
  // We don't use truthiness of alert to control whether the alert is open or not to allow a clean
  // animation when closing the alert, which requires the alert to still be rendered even when the
  // snackbar is set to closed.
  alertOpen: boolean;
}

const initialState: AppState = {
  alert: {
    message: '',
    severity: 'info',
  },
  alertOpen: false,
};

const appSlice = createSlice({
  name: 'app',
  reducers: {
    clearAlert(state): AppState {
      return {
        ...state,
        alertOpen: false,
      };
    },
    setAlert(state, action: PayloadAction<Alert>): AppState {
      return {
        ...state,
        alert: action.payload,
        alertOpen: true,
      };
    },
  },
  initialState,
});

export default appSlice;

export const { clearAlert, setAlert } = appSlice.actions;
