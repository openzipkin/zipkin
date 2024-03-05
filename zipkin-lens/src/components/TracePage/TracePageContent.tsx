/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Box, Drawer } from '@material-ui/core';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useToggle } from 'react-use';
import AdjustedTrace, { AdjustedSpan } from '../../models/AdjustedTrace';
import Span from '../../models/Span';
import { Header } from './Header/Header';
import {
  convertSpansToSpanTree,
  convertSpanTreeToSpanRowsAndTimestamps,
} from './helpers';
import { SpanDetailDrawer } from './SpanDetailDrawer';
import { SpanTable } from './SpanTable';
import { Timeline } from './Timeline';

const SPAN_DETAIL_DRAWER_WIDTH = '480px';

type TracePageContentProps = {
  trace: AdjustedTrace;
  rawTrace: Span[];
};

export const TracePageContent = ({
  trace,
  rawTrace,
}: TracePageContentProps) => {
  const [rerootedSpanId, setRerootedSpanId] = useState<string>();
  const [closedSpanIdMap, setClosedSpanIdMap] = useState<{
    [spanId: string]: boolean;
  }>({});
  const [isSpanDetailDrawerOpen, toggleIsSpanDetailDrawerOpen] =
    useToggle(true);
  const [isMiniTimelineOpen, toggleIsMiniTimelineOpen] = useToggle(true);
  const [isSpanTableOpen, toggleIsSpanTableOpen] = useToggle(false);

  const roots = useMemo(
    () => convertSpansToSpanTree(trace.spans),
    [trace.spans],
  );

  const { spanRows, minTimestamp, maxTimestamp } = useMemo(
    () =>
      convertSpanTreeToSpanRowsAndTimestamps(
        roots,
        closedSpanIdMap,
        rerootedSpanId,
      ),
    [closedSpanIdMap, rerootedSpanId, roots],
  );

  const toggleOpenSpan = useCallback((spanId: string) => {
    setClosedSpanIdMap((prev) => ({
      ...prev,
      [spanId]: !prev[spanId],
    }));
  }, []);

  const [selectedSpan, setSelectedSpan] = useState<AdjustedSpan>(spanRows[0]);

  const [selectedMinTimestamp, setSelectedMinTimestamp] =
    useState(minTimestamp);
  const [selectedMaxTimestamp, setSelectedMaxTimestamp] =
    useState(maxTimestamp);
  useEffect(() => {
    setSelectedMinTimestamp(minTimestamp);
    setSelectedMaxTimestamp(maxTimestamp);
  }, [maxTimestamp, minTimestamp]);

  return (
    <Box display="flex" flexDirection="column" height="calc(100vh - 64px)">
      <Box flex="0 0">
        <Header
          trace={trace}
          rawTrace={rawTrace}
          toggleIsSpanTableOpen={toggleIsSpanTableOpen}
        />
      </Box>
      <Box flex="1 1" display="flex" overflow="hidden">
        <Box flex="1 1">
          <Timeline
            spanRows={spanRows}
            selectedSpan={selectedSpan}
            setSelectedSpan={setSelectedSpan}
            minTimestamp={minTimestamp}
            maxTimestamp={maxTimestamp}
            selectedMinTimestamp={selectedMinTimestamp}
            selectedMaxTimestamp={selectedMaxTimestamp}
            setSelectedMinTimestamp={setSelectedMinTimestamp}
            setSelectedMaxTimestamp={setSelectedMaxTimestamp}
            isSpanDetailDrawerOpen={isSpanDetailDrawerOpen}
            toggleIsSpanDetailDrawerOpen={toggleIsSpanDetailDrawerOpen}
            isMiniTimelineOpen={isMiniTimelineOpen}
            toggleIsMiniTimelineOpen={toggleIsMiniTimelineOpen}
            rerootedSpanId={rerootedSpanId}
            setRerootedSpanId={setRerootedSpanId}
            toggleOpenSpan={toggleOpenSpan}
            setClosedSpanIdMap={setClosedSpanIdMap}
          />
        </Box>
        {isSpanDetailDrawerOpen && (
          <Box
            flex={`0 0 ${SPAN_DETAIL_DRAWER_WIDTH}`}
            height="100%"
            overflow="auto"
          >
            {selectedSpan && (
              <SpanDetailDrawer
                minTimestamp={minTimestamp}
                span={selectedSpan}
              />
            )}
          </Box>
        )}
      </Box>
      <Drawer
        anchor="right"
        open={isSpanTableOpen}
        onClose={toggleIsSpanTableOpen}
      >
        <Box width="70vw" height="100vh">
          <SpanTable
            spans={trace.spans}
            setSelectedSpan={setSelectedSpan}
            toggleIsSpanTableOpen={toggleIsSpanTableOpen}
          />
        </Box>
      </Drawer>
    </Box>
  );
};
