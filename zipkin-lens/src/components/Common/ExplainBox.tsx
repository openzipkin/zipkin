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

import { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Box, Typography } from '@material-ui/core';
import React from 'react';

interface ExplainBoxProps {
  icon: IconDefinition;
  headerText: React.ReactNode;
  text: React.ReactNode;
}

const ExplainBox = React.memo<ExplainBoxProps>(({ icon, headerText, text }) => {
  return (
    <Box
      top={64}
      left={0}
      right={0}
      bottom={0}
      position="fixed"
      display="flex"
      alignItems="center"
      justifyContent="center"
      flexDirection="column"
      color="text.secondary"
      zIndex={-1}
    >
      <FontAwesomeIcon icon={icon} size="10x" />
      <Box mt={3} mb={2}>
        <Typography variant="h4">{headerText}</Typography>
      </Box>
      <Typography variant="body1">{text}</Typography>
    </Box>
  );
});

export default ExplainBox;
