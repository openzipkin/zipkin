/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Box, makeStyles } from '@material-ui/core';
import React, { useMemo } from 'react';
import { AdjustedAnnotation } from '../../../models/AdjustedTrace';
import { AnnotationTooltip } from '../AnnotationTooltip';

const useStyles = makeStyles((theme) => ({
  annotationMarker: {
    position: 'absolute',
    backgroundColor: theme.palette.common.black,
    height: 12,
    width: 1,
    top: -6,
    cursor: 'pointer',
    pointerEvents: 'auto',
  },
}));

type TimelineRowAnnotationProps = {
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
  annotation: AdjustedAnnotation;
};

export const TimelineRowAnnotation = ({
  selectedMinTimestamp,
  selectedMaxTimestamp,
  annotation,
}: TimelineRowAnnotationProps) => {
  const classes = useStyles();

  const left = useMemo(() => {
    if (
      annotation.timestamp < selectedMinTimestamp ||
      annotation.timestamp > selectedMaxTimestamp
    ) {
      return undefined;
    }
    const p =
      ((annotation.timestamp - selectedMinTimestamp) /
        (selectedMaxTimestamp - selectedMinTimestamp)) *
      100;
    if (p >= 100) {
      return `calc(100% - 1px)`;
    }
    return `${p}%`;
  }, [annotation.timestamp, selectedMaxTimestamp, selectedMinTimestamp]);

  if (left === undefined) {
    return null;
  }

  return (
    <AnnotationTooltip annotation={annotation}>
      <Box left={left} className={classes.annotationMarker} />
    </AnnotationTooltip>
  );
};
