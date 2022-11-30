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
import { AdjustedSpan } from '../../../models/AdjustedTrace';
import { TickMarkers } from '../TickMarkers';
import { AnnotationTable } from './AnnotationTable';

const useStyles = makeStyles<Theme, { serviceName: string }>((theme) => ({
  bar: {
    width: '100%',
    height: 10,
    backgroundColor: ({ serviceName }) => selectServiceColor(serviceName),
    position: 'relative',
  },
  annotationMarker: {
    position: 'absolute',
    backgroundColor: theme.palette.common.white,
    height: 6,
    width: 2,
    top: 2,
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
        {span.timestamp && span.duration && (
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
                  <Box
                    key={`${annotation.value}-${annotation.timestamp}`}
                    className={classes.annotationMarker}
                    style={{
                      left: `calc(${
                        ((annotation.timestamp - span.timestamp) /
                          span.duration) *
                        100
                      }% - 1px)`,
                    }}
                  />
                ))}
            </Box>
          </Box>
        )}
        <AnnotationTable span={span} />
      </Collapse>
    </Box>
  );
};