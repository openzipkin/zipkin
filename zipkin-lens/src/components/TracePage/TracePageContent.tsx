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

import { Box } from '@material-ui/core';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useToggle } from 'react-use';
import AdjustedTrace, { AdjustedSpan } from '../../models/AdjustedTrace';
import { Header } from './Header';
import {
  convertSpansToSpanTree,
  convertSpanTreeToSpanRows,
} from './helpers/convert';
import { SpanDetailDrawer } from './SpanDetailDrawer';
import { SpanTable } from './SpanTable';
import { Timeline } from './Timeline';

type TracePageContentProps = {
  trace: AdjustedTrace;
};

export const TracePageContent = ({ trace }: TracePageContentProps) => {
  const [rerootedSpanId, setRerootedSpanId] = useState<string>();
  const [closedSpanIdMap, setClosedSpanIdMap] = useState<{
    [spanId: string]: boolean;
  }>({});
  const [isSpanDetailDrawerOpen, toggleIsSpanDetailDrawerOpen] = useToggle(
    true,
  );
  const [isMiniTimelineOpen, toggleIsMiniTimelineOpen] = useToggle(true);
  const [isSpanTableOpen, toggleIsSpanTableOpen] = useToggle(false);

  const roots = useMemo(() => convertSpansToSpanTree(trace.spans), [
    trace.spans,
  ]);

  const spanRows = useMemo(
    () => convertSpanTreeToSpanRows(roots, closedSpanIdMap, rerootedSpanId),
    [closedSpanIdMap, rerootedSpanId, roots],
  );

  const [minTimestamp, maxTimestamp] = useMemo(
    () => [
      Math.min(
        ...spanRows
          .filter((spanRow) => spanRow.timestamp !== undefined)
          .map((spanRow) => spanRow.timestamp),
      ),
      Math.max(
        ...spanRows
          .filter(
            (spanRow) =>
              spanRow.timestamp !== undefined && spanRow.duration !== undefined,
          )
          .map((spanRow) => spanRow.timestamp + spanRow.duration),
      ),
    ],
    [spanRows],
  );

  const [selectedSpan, setSelectedSpan] = useState<AdjustedSpan>(spanRows[0]);
  const [selectedTimeRange, setSelectedTimeRange] = useState({
    minTimestamp,
    maxTimestamp,
  });
  const setSelectedMinTimestamp = useCallback((value: number) => {
    setSelectedTimeRange((prev) => ({
      ...prev,
      minTimestamp: value,
    }));
  }, []);
  const setSelectedMaxTimestamp = useCallback((value: number) => {
    setSelectedTimeRange((prev) => ({
      ...prev,
      maxTimestamp: value,
    }));
  }, []);

  useEffect(() => {
    setSelectedSpan(spanRows[0]);
  }, [spanRows]);

  useEffect(() => {
    setSelectedTimeRange({
      minTimestamp,
      maxTimestamp,
    });
  }, [maxTimestamp, minTimestamp]);

  return (
    <Box display="flex" flexDirection="column" height="calc(100vh - 64px)">
      <Box flex="0 0">
        <Header trace={trace} />
      </Box>
      <Box flex="1 1" display="flex" overflow="hidden">
        <Box flex="1 1" display="flex" flexDirection="column">
          {isSpanTableOpen && (
            <Box flex="0 0 260px">
              <SpanTable
                spans={trace.spans}
                setSelectedSpan={setSelectedSpan}
              />
            </Box>
          )}
          <Box flex="1 1">
            <Timeline
              spanRows={spanRows}
              selectedSpan={selectedSpan}
              setSelectedSpan={setSelectedSpan}
              minTimestamp={minTimestamp}
              maxTimestamp={maxTimestamp}
              selectedMinTimestamp={selectedTimeRange.minTimestamp}
              selectedMaxTimestamp={selectedTimeRange.maxTimestamp}
              setSelectedMinTimestamp={setSelectedMinTimestamp}
              setSelectedMaxTimestamp={setSelectedMaxTimestamp}
              isSpanDetailDrawerOpen={isSpanDetailDrawerOpen}
              toggleIsSpanDetailDrawerOpen={toggleIsSpanDetailDrawerOpen}
              isMiniTimelineOpen={isMiniTimelineOpen}
              toggleIsMiniTimelineOpen={toggleIsMiniTimelineOpen}
              isSpanTableOpen={isSpanTableOpen}
              toggleIsSpanTableOpen={toggleIsSpanTableOpen}
              rerootedSpanId={rerootedSpanId}
              setRerootedSpanId={setRerootedSpanId}
            />
          </Box>
        </Box>
        {isSpanDetailDrawerOpen && (
          <Box flex="0 0 500px" height="100%" overflow="auto">
            {selectedSpan && (
              <SpanDetailDrawer
                minTimestamp={minTimestamp}
                span={selectedSpan}
              />
            )}
          </Box>
        )}
      </Box>
    </Box>
  );
};