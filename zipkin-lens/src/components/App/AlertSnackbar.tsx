/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
