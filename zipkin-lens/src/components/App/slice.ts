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
