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

import { makeStyles } from '@material-ui/core';
import React, { useRef } from 'react';
import { SpanRow } from '../types';
import { OverlayTimeRangeSelector } from './OverlayTimeRangeSelector';
import { TimeRangeSelector } from './TimeRangeSelector';
import { MiniTimelineRow } from './MiniTimelineRow';

const useStyles = makeStyles((theme) => ({
  root: {
    width: '100%',
    height: 50,
    border: `1px solid ${theme.palette.divider}`,
    backgroundColor: theme.palette.background.paper,
  },
}));

type MiniTimelineProps = {
  spanRows: SpanRow[];
  minTimestamp: number;
  maxTimestamp: number;
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
  setSelectedMinTimestamp: (value: number) => void;
  setSelectedMaxTimestamp: (value: number) => void;
};

export const MiniTimeline = ({
  spanRows,
  minTimestamp,
  maxTimestamp,
  selectedMinTimestamp,
  selectedMaxTimestamp,
  setSelectedMinTimestamp,
  setSelectedMaxTimestamp,
}: MiniTimelineProps) => {
  const classes = useStyles();
  const rootEl = useRef<SVGSVGElement | null>(null);

  return (
    <svg className={classes.root} ref={rootEl}>
      {spanRows.map((spanRow, i) => (
        <MiniTimelineRow
          key={spanRow.spanId}
          top={(100 / spanRows.length) * i}
          spanRow={spanRow}
          minTimestamp={minTimestamp}
          maxTimestamp={maxTimestamp}
        />
      ))}
      <OverlayTimeRangeSelector
        minTimestamp={minTimestamp}
        maxTimestamp={maxTimestamp}
        setSelectedMinTimestamp={setSelectedMinTimestamp}
        setSelectedMaxTimestamp={setSelectedMaxTimestamp}
      />
      <TimeRangeSelector
        rootEl={rootEl}
        minTimestamp={minTimestamp}
        maxTimestamp={maxTimestamp}
        selectedMinTimestamp={selectedMinTimestamp}
        selectedMaxTimestamp={selectedMaxTimestamp}
        setSelectedMinTimestamp={setSelectedMinTimestamp}
        setSelectedMaxTimestamp={setSelectedMaxTimestamp}
      />
    </svg>
  );
};
