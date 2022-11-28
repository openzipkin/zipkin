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
import React, { useMemo } from 'react';
import { selectServiceColor } from '../../../constants/color';
import { SpanRow } from '../types';

const useStyles = makeStyles<Theme, { serviceName: string }>(() => ({
  bar: {
    position: 'absolute',
    height: 4,
    transform: `translateY(-1px)`,
    backgroundColor: ({ serviceName }) => selectServiceColor(serviceName),
  },
}));

type MiniTimelineRowProps = {
  top: number;
  spanRow: SpanRow;
  minTimestamp: number;
  maxTimestamp: number;
};

export const MiniTimelineRow = ({
  top,
  spanRow,
  minTimestamp,
  maxTimestamp,
}: MiniTimelineRowProps) => {
  const classes = useStyles({ serviceName: spanRow.serviceName });

  const left = useMemo(
    () =>
      spanRow.timestamp
        ? ((spanRow.timestamp - minTimestamp) / (maxTimestamp - minTimestamp)) *
          100
        : 0,
    [maxTimestamp, minTimestamp, spanRow.timestamp],
  );

  const width = useMemo(
    () =>
      left !== undefined && spanRow.duration && spanRow.timestamp
        ? Math.max(
            ((spanRow.timestamp + spanRow.duration - minTimestamp) /
              (maxTimestamp - minTimestamp)) *
              100 -
              left,
            0.1,
          )
        : 0.1,
    [left, maxTimestamp, minTimestamp, spanRow.duration, spanRow.timestamp],
  );

  return (
    <Box
      className={classes.bar}
      top={`${top}%`}
      left={`${left}%`}
      width={`${width}%`}
    />
  );
};
