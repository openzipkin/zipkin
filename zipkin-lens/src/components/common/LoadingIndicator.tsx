/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Box, CircularProgress } from '@material-ui/core';
import React from 'react';

export const LoadingIndicator = () => (
  <Box
    display="flex"
    justifyContent="center"
    mt={10}
    data-testid="loading-indicator"
  >
    <CircularProgress />
  </Box>
);
