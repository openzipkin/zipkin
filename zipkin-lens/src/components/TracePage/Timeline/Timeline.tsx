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
import React, { useCallback, useMemo } from 'react';
import { AutoSizer, List, ListRowProps } from 'react-virtualized';
import { AdjustedSpan } from '../../../models/AdjustedTrace';
import { SpanRow } from '../types';
import { MiniTimeline } from './MiniTimeline';
import { TickMarkers } from './TickMarkers';
import { TimelineHeader } from './TimelineHeader';
import { TimelineRow } from './TimelineRow';

const useStyles = makeStyles((theme) => ({
  root: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    backgroundColor: theme.palette.background.paper,
  },
  miniViewerContainer: {
    flex: '0 0',
    padding: theme.spacing(1),
    backgroundColor: theme.palette.grey[50],
    borderBottom: `1px solid ${theme.palette.divider}`,
  },
}));

type TimelineProps = {
  spanRows: SpanRow[];
  selectedSpan?: AdjustedSpan;
  minTimestamp: number;
  maxTimestamp: number;
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
};

const rowHeight = 30;

export const Timeline = ({
  spanRows,
  selectedSpan,
  minTimestamp,
  maxTimestamp,
  selectedMinTimestamp,
  selectedMaxTimestamp,
}: TimelineProps) => {
  const classes = useStyles();

  const scrollToIndex = useMemo(() => {
    if (selectedSpan) {
      const index = spanRows.findIndex((r) => r.spanId === selectedSpan.spanId);
      return index === -1 ? undefined : index;
    }
    return undefined;
  }, [selectedSpan, spanRows]);

  const rowRenderer = useCallback(
    (props: ListRowProps) => {
      const spanRow = spanRows[props.index];

      return (
        <div key={props.key} style={props.style}>
          <TimelineRow
            key={props.key}
            {...spanRow}
            selectedMinTimestamp={selectedMinTimestamp}
            selectedMaxTimestamp={selectedMaxTimestamp}
          />
        </div>
      );
    },
    [selectedMaxTimestamp, selectedMinTimestamp, spanRows],
  );

  return (
    <Box className={classes.root}>
      <Box className={classes.miniViewerContainer}>
        <TickMarkers
          minTimestamp={0}
          maxTimestamp={maxTimestamp - minTimestamp}
        />
        <MiniTimeline
          spanRows={spanRows}
          minTimestamp={minTimestamp}
          maxTimestamp={maxTimestamp}
          selectedMinTimestamp={selectedMinTimestamp}
          selectedMaxTimestamp={selectedMaxTimestamp}
        />
      </Box>
      <Box flex="0 0">
        <TimelineHeader
          minTimestamp={minTimestamp}
          selectedMinTimestamp={selectedMinTimestamp}
          selectedMaxTimestamp={selectedMaxTimestamp}
        />
      </Box>
      <Box flex="1 1">
        <AutoSizer>
          {({ width, height }) => (
            <List
              width={width}
              height={height}
              rowHeight={rowHeight}
              rowCount={spanRows.length}
              rowRenderer={rowRenderer}
              scrollToIndex={scrollToIndex}
            />
          )}
        </AutoSizer>
      </Box>
    </Box>
  );
};
