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

import { Box, Divider, Grid, makeStyles, Typography } from '@material-ui/core';
import React from 'react';
import { AdjustedSpan } from '../../../models/AdjustedTrace';
import { AnnotationViewer } from './AnnotationViewer';

const useStyles = makeStyles((theme) => ({
  root: {
    padding: theme.spacing(2),
    height: '100%',
    backgroundColor: theme.palette.background.paper,
    borderLeft: `1px solid ${theme.palette.divider}`,
    display: 'flex',
    flexDirection: 'column',
  },
  basicInfoLabel: {
    lineHeight: 1.2,
  },
  divider: {
    marginTop: theme.spacing(1.5),
    marginBottom: theme.spacing(1.5),
  },
}));

type SpanDetailDrawerProps = {
  span: AdjustedSpan;
  minTimestamp: number;
};

export const SpanDetailDrawer = ({
  span,
  minTimestamp,
}: SpanDetailDrawerProps) => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      <Grid container spacing={1}>
        {[
          { label: 'Service name', value: span.serviceName },
          { label: 'Span name', value: span.spanName },
          { label: 'Span ID', value: span.spanId },
          { label: 'Parent ID', value: span.parentId || 'none' },
        ].map(({ label, value }) => (
          <Grid item xs={6}>
            <Typography
              variant="caption"
              color="textSecondary"
              className={classes.basicInfoLabel}
            >
              {label}
            </Typography>
            <Typography variant="body1">{value}</Typography>
          </Grid>
        ))}
      </Grid>
      <Divider className={classes.divider} />
      <AnnotationViewer minTimestamp={minTimestamp} span={span} />
    </Box>
  );
};
