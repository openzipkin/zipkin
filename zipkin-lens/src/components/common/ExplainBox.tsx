/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
      height="100%"
      width="100%"
      display="flex"
      alignItems="center"
      justifyContent="center"
      flexDirection="column"
      color="text.secondary"
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
