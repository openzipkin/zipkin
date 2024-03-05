/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  Box,
  Collapse,
  IconButton,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import {
  KeyboardArrowDown as KeyboardArrowDownIcon,
  KeyboardArrowUp as KeyboardArrowUpIcon,
} from '@material-ui/icons';
import React from 'react';
import { useToggle } from 'react-use';
import { selectServiceColor } from '../../../constants/color';
import {
  AdjustedAnnotation,
  AdjustedSpan,
} from '../../../models/AdjustedTrace';
import { AnnotationTable } from '../AnnotationTable';
import { AnnotationTooltip } from '../AnnotationTooltip';
import { TickMarkers } from '../TickMarkers';

const calculateMarkerLeftPosition = (
  annotation: AdjustedAnnotation,
  span: AdjustedSpan,
) => {
  const p = ((annotation.timestamp - span.timestamp) / span.duration) * 100;
  if (p >= 100) {
    return 'calc(100% - 1px)';
  }
  return `${p}%`;
};

const useStyles = makeStyles<Theme, { serviceName: string }>((theme) => ({
  bar: {
    width: '100%',
    height: 10,
    backgroundColor: ({ serviceName }) => selectServiceColor(serviceName),
    position: 'relative',
  },
  annotationMarker: {
    position: 'absolute',
    backgroundColor: theme.palette.common.black,
    height: 18,
    width: 1,
    top: -4,
    cursor: 'pointer',
  },
}));

type AnnotationViewerProps = {
  minTimestamp: number;
  span: AdjustedSpan;
};

export const AnnotationViewer = ({
  minTimestamp,
  span,
}: AnnotationViewerProps) => {
  const classes = useStyles({ serviceName: span.serviceName });
  const [open, toggleOpen] = useToggle(true);

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Typography>Annotation</Typography>
        <IconButton onClick={toggleOpen} size="small">
          {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
        </IconButton>
      </Box>
      <Collapse in={open}>
        {span.timestamp && span.duration ? (
          <Box mt={1.5} mb={1.5}>
            <TickMarkers
              minTimestamp={span.timestamp - minTimestamp}
              maxTimestamp={span.timestamp + span.duration - minTimestamp}
            />
            <Box className={classes.bar}>
              {span.annotations
                .filter(
                  (annotation) =>
                    annotation.timestamp &&
                    annotation.timestamp >= span.timestamp &&
                    annotation.timestamp <= span.timestamp + span.duration,
                )
                .map((annotation) => (
                  <AnnotationTooltip
                    key={`${annotation.value}-${annotation.timestamp}`}
                    annotation={annotation}
                  >
                    <Box
                      key={`${annotation.value}-${annotation.timestamp}`}
                      className={classes.annotationMarker}
                      style={{
                        left: calculateMarkerLeftPosition(annotation, span),
                      }}
                    />
                  </AnnotationTooltip>
                ))}
            </Box>
          </Box>
        ) : null}
        <AnnotationTable annotations={span.annotations} />
      </Collapse>
    </Box>
  );
};
