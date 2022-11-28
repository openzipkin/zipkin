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

import { Box, makeStyles } from '@material-ui/core';
import React from 'react';
import { SpanRow } from '../types';
import { TimelineHeader } from './TimelineHeader';
import { TimelineRow } from './TimelineRow';

const useStyles = makeStyles((theme) => ({
  root: {
    backgroundColor: theme.palette.background.paper,
  },
}));

type TimelineProps = {
  spanRows: SpanRow[];
  timeRange: [number, number];
};

export const Timeline = ({ spanRows, timeRange }: TimelineProps) => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      <TimelineHeader />
      {spanRows.map((spanRow) => (
        <TimelineRow {...spanRow} timeRange={timeRange} />
      ))}
    </Box>
  );
};
