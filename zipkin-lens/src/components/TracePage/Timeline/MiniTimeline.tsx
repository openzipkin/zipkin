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
import { MiniTimelineRow } from './MiniTimelineRow';

const useStyles = makeStyles((theme) => ({
  root: {
    height: 60,
    position: 'relative',
    border: `1px solid ${theme.palette.divider}`,
    backgroundColor: theme.palette.background.paper,
  },
}));

type MiniTimelineProps = {
  spanRows: SpanRow[];
  minTimestamp: number;
  maxTimestamp: number;
  timeRange: [number, number];
};

export const MiniTimeline = ({
  spanRows,
  minTimestamp,
  maxTimestamp,
  timeRange,
}: MiniTimelineProps) => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      {spanRows.map((spanRow, i) => (
        <MiniTimelineRow
          top={(100 / spanRows.length) * i}
          spanRow={spanRow}
          minTimestamp={minTimestamp}
          maxTimestamp={maxTimestamp}
        />
      ))}
    </Box>
  );
};
