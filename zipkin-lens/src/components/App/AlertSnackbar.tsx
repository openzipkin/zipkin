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

import Snackbar from '@material-ui/core/Snackbar';
import Alert from '@material-ui/lab/Alert';
import React, { useCallback } from 'react';
import { useDispatch, useSelector } from 'react-redux';

import { RootState } from '../../store';

import { clearAlert } from './slice';

/**
 * Renders a message at the top of the page, usually to indicate success / failure of a background
 * action.
 */
const AlertSnackbar: React.FC = () => {
  const dispatch = useDispatch();
  const { alert, alertOpen } = useSelector((state: RootState) => state.app);
  const onSnackbarClose = useCallback(() => dispatch(clearAlert()), [dispatch]);
  return (
    <Snackbar
      open={alertOpen}
      autoHideDuration={3000}
      onClose={onSnackbarClose}
      anchorOrigin={{
        vertical: 'top',
        horizontal: 'center',
      }}
    >
      <Alert elevation={6} variant="filled" severity={alert.severity}>
        {alert.message}
      </Alert>
    </Snackbar>
  );
};

export default AlertSnackbar;
