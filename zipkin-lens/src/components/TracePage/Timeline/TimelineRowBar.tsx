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

import { Box, makeStyles, Theme } from '@material-ui/core';
import React from 'react';
import { selectServiceColor } from '../../../constants/color';
import { SpanRow } from '../types';

const useStyles = makeStyles<Theme, { rowHeight: number; serviceName: string }>(
  (theme) => ({
    root: {
      left: 0,
      right: 0,
      top: 0,
      bottom: 0,
      position: 'absolute',
      transform: ({ rowHeight }) => `translateY(${(rowHeight / 4) * 3}px)`,
      pointerEvents: 'none',
    },
    line: {
      position: 'absolute',
      left: 0,
      right: 0,
      borderBottom: `1px solid ${theme.palette.divider}`,
    },
    bar: {
      position: 'absolute',
      height: 6,
      transform: `translateY(-3px)`,
      borderRadius: 3,
      backgroundColor: ({ serviceName }) => selectServiceColor(serviceName),
    },
  }),
);

type TimelineRowBarProps = { spanRow: SpanRow; rowHeight: number };

export const TimelineRowBar = ({ spanRow, rowHeight }: TimelineRowBarProps) => {
  const classes = useStyles({ rowHeight, serviceName: spanRow.serviceName });

  return (
    <Box className={classes.root}>
      <Box className={classes.line} />
      <Box
        className={classes.bar}
        left={spanRow.left !== undefined ? `${spanRow.left}%` : undefined}
        width={spanRow.width !== undefined ? `${spanRow.width}%` : undefined}
      />
    </Box>
  );
};
