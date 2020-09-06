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

import {
  Backdrop,
  CircularProgress,
  Theme,
  createStyles,
  makeStyles,
} from '@material-ui/core';
import { SerializedError } from '@reduxjs/toolkit';
import React, { useEffect } from 'react';
import { useDispatch } from 'react-redux';

import { setAlert } from '../App/slice';

interface LoadingOverlayProps {
  isLoading: boolean;
  error?: SerializedError;
}

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    backdrop: {
      zIndex: theme.zIndex.drawer + 1,
      color: '#fff',
    },
  }),
);

const LoadingOverlay: React.FC<LoadingOverlayProps> = ({
  isLoading,
  error,
}) => {
  const classes = useStyles();

  const dispatch = useDispatch();

  useEffect(() => {
    if (error) {
      dispatch(
        setAlert({
          message: error.message || 'Error occurs when loading...',
          severity: 'error',
        }),
      );
    }
  }, [dispatch, error]);

  return (
    <div>
      <Backdrop className={classes.backdrop} open={isLoading}>
        <CircularProgress color="inherit" />
      </Backdrop>
    </div>
  );
};

export default LoadingOverlay;
