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
import { ErrorOutline as ErrorOutlineIcon } from '@material-ui/icons';
import classNames from 'classnames';
import React, { useCallback } from 'react';
import { AdjustedSpan } from '../../../models/AdjustedTrace';
import { SpanRow } from '../types';
import { TimelineRowBar } from './TimelineRowBar';
import { TimelineRowEdge } from './TimelineRowEdges';

const useStyles = makeStyles((theme) => ({
  root: {
    display: 'flex',
    cursor: 'pointer',
    '&:hover': {
      backgroundColor: theme.palette.grey[100],
    },
  },
  rootSelected: {
    backgroundColor: theme.palette.grey.A100,
  },
  text: {
    fontSize: theme.typography.caption.fontSize,
    color: theme.palette.text.secondary,
  },
  errorIcon: {
    marginRight: theme.spacing(0.5),
  },
}));

type TimelineRowProps = SpanRow & {
  isSelected: boolean;
  setSelectedSpan: (span: AdjustedSpan) => void;
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
};

export const TimelineRow = (props: TimelineRowProps) => {
  const {
    isSelected,
    setSelectedSpan,
    serviceName,
    spanName,
    treeEdgeShape,
    durationStr,
    errorType,
    isClosed,
    isCollapsible,
    selectedMinTimestamp,
    selectedMaxTimestamp,
  } = props;
  const classes = useStyles();

  const rowHeight = 30;

  const handleClick = useCallback(() => {
    setSelectedSpan(props);
  }, [props, setSelectedSpan]);

  return (
    <Box
      className={classNames(classes.root, {
        [classes.rootSelected]: isSelected,
      })}
      onClick={handleClick}
    >
      <TimelineRowEdge
        treeEdgeShape={treeEdgeShape}
        isClosed={isClosed}
        isCollapsible={isCollapsible}
        rowHeight={rowHeight}
      />
      <Box position="relative" width="100%" flex="1 1">
        <Box pt={0.25} display="flex" justifyContent="space-between" pr={1}>
          <Box display="flex" alignItems="center">
            {errorType !== 'none' && (
              <ErrorOutlineIcon
                className={classes.errorIcon}
                fontSize="small"
                color="error"
              />
            )}
            <Box className={classes.text}>{`${serviceName}: ${spanName}`}</Box>
          </Box>
          <Box className={classes.text}>{durationStr}</Box>
        </Box>
        <TimelineRowBar
          spanRow={props}
          rowHeight={rowHeight}
          selectedMinTimestamp={selectedMinTimestamp}
          selectedMaxTimestamp={selectedMaxTimestamp}
        />
      </Box>
    </Box>
  );
};