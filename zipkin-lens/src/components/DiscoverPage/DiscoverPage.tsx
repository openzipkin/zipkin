/* eslint-disable no-shadow */
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
import React from 'react';
import { Box, Typography } from '@material-ui/core';
import { Trans } from '@lingui/macro';

import { useUiConfig } from '../UiConfig';
import TraceJsonUploader from '../Common/TraceJsonUploader';
import TraceIdSearchInput from '../Common/TraceIdSearchInput';
import DiscoverPageContent from './DiscoverPageContent';

const DiscoverPage: React.FC = () => {
  const config = useUiConfig();

  let content: JSX.Element;

  if (!config.searchEnabled) {
    content = (
      <Typography variant="body1">
        <Trans>
          Searching has been disabled via the searchEnabled property. You can
          still view specific traces of which you know the trace id by entering
          it in the "trace id..." textbox on the top-right.
        </Trans>
      </Typography>
    );
  } else {
    content = <DiscoverPageContent />;
  }

  return (
    <Box width="100%" height="100vh" display="flex" flexDirection="column">
      <Box
        pl={3}
        pr={3}
        display="flex"
        justifyContent="space-between"
        alignItems="center"
      >
        <Typography variant="h5">
          <Trans>Discover</Trans>
        </Typography>
        <Box pr={3} display="flex" alignItems="center">
          <TraceJsonUploader />
          <TraceIdSearchInput />
        </Box>
      </Box>
      {content}
    </Box>
  );
};

export default DiscoverPage;
