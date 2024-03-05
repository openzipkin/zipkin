/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Box, makeStyles } from '@material-ui/core';
import React, { useCallback, useEffect, useRef } from 'react';
import AutoSizer, { Size } from 'react-virtualized-auto-sizer';
import { FixedSizeList as List, ListChildComponentProps } from 'react-window';
import { useMeasure } from 'react-use';
import { AdjustedSpan } from '../../../models/AdjustedTrace';
import { MiniTimeline } from '../MiniTimeline';
import { TickMarkers } from '../TickMarkers';
import { SpanRow } from '../types';
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
  selectedSpan: AdjustedSpan;
  setSelectedSpan: (span: AdjustedSpan) => void;
  minTimestamp: number;
  maxTimestamp: number;
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
  setSelectedMinTimestamp: (value: number) => void;
  setSelectedMaxTimestamp: (value: number) => void;
  isSpanDetailDrawerOpen: boolean;
  toggleIsSpanDetailDrawerOpen: () => void;
  isMiniTimelineOpen: boolean;
  toggleIsMiniTimelineOpen: () => void;
  rerootedSpanId: string | undefined;
  setRerootedSpanId: (value: string | undefined) => void;
  toggleOpenSpan: (spanId: string) => void;
  setClosedSpanIdMap: (value: { [spanId: string]: boolean }) => void;
};

const rowHeight = 30;

export const Timeline = ({
  spanRows,
  selectedSpan,
  setSelectedSpan,
  minTimestamp,
  maxTimestamp,
  selectedMinTimestamp,
  selectedMaxTimestamp,
  setSelectedMinTimestamp,
  setSelectedMaxTimestamp,
  isSpanDetailDrawerOpen,
  toggleIsSpanDetailDrawerOpen,
  isMiniTimelineOpen,
  toggleIsMiniTimelineOpen,
  rerootedSpanId,
  setRerootedSpanId,
  toggleOpenSpan,
  setClosedSpanIdMap,
}: TimelineProps) => {
  const classes = useStyles();

  const listRef = useRef<any>();
  const [listInnerRef, listInnerMeasure] = useMeasure();

  const prevSelectedSpanId = useRef(selectedSpan.spanId);
  useEffect(() => {
    if (selectedSpan.spanId !== prevSelectedSpanId.current) {
      listRef.current.scrollToItem(
        spanRows.findIndex((r) => r.spanId === selectedSpan.spanId),
        'center',
      );
    }
  }, [selectedSpan.spanId, spanRows]);

  const rowRenderer = useCallback(
    (props: ListChildComponentProps) => {
      const spanRow = spanRows[props.index];

      return (
        <div style={props.style}>
          <TimelineRow
            {...spanRow}
            setSelectedSpan={setSelectedSpan}
            isSelected={selectedSpan.spanId === spanRow.spanId}
            selectedMinTimestamp={selectedMinTimestamp}
            selectedMaxTimestamp={selectedMaxTimestamp}
            toggleOpenSpan={toggleOpenSpan}
            rowHeight={rowHeight}
          />
        </div>
      );
    },
    [
      selectedMaxTimestamp,
      selectedMinTimestamp,
      selectedSpan.spanId,
      setSelectedSpan,
      spanRows,
      toggleOpenSpan,
    ],
  );

  return (
    <Box className={classes.root}>
      {isMiniTimelineOpen && (
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
            setSelectedMinTimestamp={setSelectedMinTimestamp}
            setSelectedMaxTimestamp={setSelectedMaxTimestamp}
          />
        </Box>
      )}
      <Box flex="0 0">
        <TimelineHeader
          spanRows={spanRows}
          minTimestamp={minTimestamp}
          selectedMinTimestamp={selectedMinTimestamp}
          selectedMaxTimestamp={selectedMaxTimestamp}
          isSpanDetailDrawerOpen={isSpanDetailDrawerOpen}
          toggleIsSpanDetailDrawerOpen={toggleIsSpanDetailDrawerOpen}
          isMiniTimelineOpen={isMiniTimelineOpen}
          toggleIsMiniTimelineOpen={toggleIsMiniTimelineOpen}
          selectedSpan={selectedSpan}
          rerootedSpanId={rerootedSpanId}
          setRerootedSpanId={setRerootedSpanId}
          absoluteListWidth={listInnerMeasure.width}
          setClosedSpanIdMap={setClosedSpanIdMap}
        />
      </Box>
      <Box flex="1 1">
        <AutoSizer>
          {(args: Size) => (
            <List
              ref={listRef}
              width={args.width}
              height={args.height}
              itemSize={rowHeight}
              itemCount={spanRows.length}
              innerRef={listInnerRef}
            >
              {rowRenderer}
            </List>
          )}
        </AutoSizer>
      </Box>
    </Box>
  );
};
