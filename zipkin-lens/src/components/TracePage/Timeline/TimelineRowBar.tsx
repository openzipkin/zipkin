/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Box, makeStyles, Theme } from '@material-ui/core';
import React, { useMemo } from 'react';
import { selectServiceColor } from '../../../constants/color';
import { SpanRow } from '../types';
import { TimelineRowAnnotation } from './TimelineRowAnnotation';

const useStyles = makeStyles<Theme, { rowHeight: number; serviceName: string }>(
  (theme) => ({
    root: {
      left: 0,
      right: theme.spacing(1),
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
      backgroundColor: ({ serviceName }) => selectServiceColor(serviceName),
    },
  }),
);

type TimelineRowBarProps = {
  spanRow: SpanRow;
  rowHeight: number;
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
};

export const TimelineRowBar = ({
  spanRow,
  rowHeight,
  selectedMinTimestamp,
  selectedMaxTimestamp,
}: TimelineRowBarProps) => {
  const classes = useStyles({ rowHeight, serviceName: spanRow.serviceName });

  const { left, width } = useMemo(() => {
    const l = spanRow.timestamp
      ? ((spanRow.timestamp - selectedMinTimestamp) /
          (selectedMaxTimestamp - selectedMinTimestamp)) *
        100
      : 0;

    const r =
      spanRow.duration && spanRow.timestamp
        ? ((spanRow.timestamp + spanRow.duration - selectedMinTimestamp) /
            (selectedMaxTimestamp - selectedMinTimestamp)) *
          100
        : 0;

    let rl: number | undefined;
    let rw: number | undefined;
    if (l <= 0 && r <= 0) {
      rl = undefined;
      rw = undefined;
    } else if (l <= 0 && r > 0) {
      rl = 0;
      rw = r;
    } else if (l >= 100) {
      rl = undefined;
      rw = undefined;
    } else if (r >= 100) {
      rl = l;
      rw = 100 - rl;
    } else {
      rl = l;
      rw = r - l;
    }
    return { left: rl, width: rw };
  }, [
    selectedMaxTimestamp,
    selectedMinTimestamp,
    spanRow.duration,
    spanRow.timestamp,
  ]);

  return (
    <Box className={classes.root}>
      <Box className={classes.line} />
      {left !== undefined && width !== undefined ? (
        <Box className={classes.bar} left={`${left}%`} width={`${width}%`} />
      ) : null}
      {spanRow.annotations.map((annotation) => (
        <TimelineRowAnnotation
          key={`${annotation.value}-${annotation.timestamp}`}
          selectedMinTimestamp={selectedMinTimestamp}
          selectedMaxTimestamp={selectedMaxTimestamp}
          annotation={annotation}
        />
      ))}
    </Box>
  );
};
