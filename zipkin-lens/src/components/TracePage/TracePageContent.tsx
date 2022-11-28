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
import React, { useMemo, useState } from 'react';
import AdjustedTrace from '../../models/AdjustedTrace';
import { Header } from './Header';
import {
  convertSpansToSpanTree,
  convertSpanTreeToSpanRows,
} from './helpers/convert';
import { Timeline } from './Timeline';

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
    () =>
      convertSpanTreeToSpanRows(
        roots,
        trace.spans,
        closedSpanIdMap,
        rerootedSpanId,
      ),
    [closedSpanIdMap, rerootedSpanId, roots, trace.spans],
  );

  return (
    <Box>
      <Header trace={trace} />
      <Timeline spanRows={spanRows} />
    </Box>
  );
};
