/*
 * Copyright 2015-2022 The OpenZipkin Authors
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

import { Box, makeStyles, Typography } from '@material-ui/core';
import React from 'react';
import { AdjustedSpan } from '../../../models/AdjustedTrace';

const useStyles = makeStyles((theme) => ({
  root: {
    height: '100%',
    backgroundColor: theme.palette.background.paper,
    borderLeft: `1px solid ${theme.palette.divider}`,
    display: 'flex',
    flexDirection: 'column',
  },
  basicInfoWrapper: {
    padding: theme.spacing(2),
    '& > :not(:last-child)': {
      marginBottom: theme.spacing(1),
    },
  },
}));

type SpanDetailDrawerProps = {
  span: AdjustedSpan;
};

export const SpanDetailDrawer = ({ span }: SpanDetailDrawerProps) => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      <Box className={classes.basicInfoWrapper}>
        {[
          { label: 'Service name', value: span.serviceName },
          { label: 'Span name', value: span.spanName },
          { label: 'Span ID', value: span.spanId },
          { label: 'Parent ID', value: span.parentId || 'none' },
        ].map(({ label, value }) => (
          <Box>
            <Typography variant="caption" color="textSecondary">
              {label}
            </Typography>
            <Typography variant="body1">{value}</Typography>
          </Box>
        ))}
      </Box>
    </Box>
  );
};
