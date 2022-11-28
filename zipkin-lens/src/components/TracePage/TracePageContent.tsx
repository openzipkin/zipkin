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
import React, { useEffect, useMemo, useState } from 'react';
import AdjustedTrace, { AdjustedSpan } from '../../models/AdjustedTrace';
import { Header } from './Header';
import {
  convertSpansToSpanTree,
  convertSpanTreeToSpanRows,
} from './helpers/convert';
import { Timeline } from './Timeline';
import { SpanRow } from './types';

type TracePageContentProps = {
  trace: AdjustedTrace;
};

export const TracePageContent = ({ trace }: TracePageContentProps) => {
  const [rerootedSpanId, setRerootedSpanId] = useState<string>();
  const [closedSpanIdMap, setClosedSpanIdMap] = useState<{
    [spanId: string]: boolean;
  }>({});

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

  const [selectedSpanRow, setSelectedSpanRow] = useState<SpanRow>(spanRows[0]);
  const [selectedTimeRange, setSelectedTimeRange] = useState({
    minTimestamp,
    maxTimestamp,
  });

  useEffect(() => {
    setSelectedSpanRow(spanRows[0]);
  }, [spanRows]);

  useEffect(() => {
    setSelectedTimeRange({
      minTimestamp,
      maxTimestamp,
    });
  }, [maxTimestamp, minTimestamp]);

  return (
    <Box display="flex" flexDirection="column">
      <Box flex="1 1">
        <Header trace={trace} />
      </Box>
      <Box flex="0 0" display="flex">
        <Box flex="1 1">
          <Timeline
            spanRows={spanRows}
            minTimestamp={minTimestamp}
            maxTimestamp={maxTimestamp}
            selectedMinTimestamp={selectedTimeRange.minTimestamp}
            selectedMaxTimestamp={selectedTimeRange.maxTimestamp}
          />
        </Box>
        <Box flex="0 0">
          <Box width={320}>Span Detail</Box>
        </Box>
      </Box>
    </Box>
  );
};
