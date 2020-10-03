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

import { Box, useTheme } from '@material-ui/core';
import React from 'react';
import { ScaleLoader } from 'react-spinners';

export const LoadingIndicator: React.FC = () => {
  const theme = useTheme();

  return (
    <Box
      display="flex"
      justifyContent="center"
      mt={10}
      mb={10}
      data-testid="loading-indicator"
    >
      <ScaleLoader color={theme.palette.primary.light} />
    </Box>
  );
};
