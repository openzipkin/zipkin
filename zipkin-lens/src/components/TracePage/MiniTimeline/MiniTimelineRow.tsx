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

import React, { useMemo } from 'react';
import { selectServiceColor } from '../../../constants/color';
import { adjustPercentValue } from '../helpers';
import { SpanRow } from '../types';

type MiniTimelineRowProps = {
  top: number;
  spanRow: SpanRow;
  minTimestamp: number;
  maxTimestamp: number;
};

export const MiniTimelineRow = ({
  top,
  spanRow,
  minTimestamp,
  maxTimestamp,
}: MiniTimelineRowProps) => {
  const left = useMemo(
    () =>
      adjustPercentValue(
        spanRow.timestamp
          ? ((spanRow.timestamp - minTimestamp) /
              (maxTimestamp - minTimestamp)) *
              100
          : 0,
      ),
    [maxTimestamp, minTimestamp, spanRow.timestamp],
  );

  const width = useMemo(
    () =>
      adjustPercentValue(
        left !== undefined && spanRow.duration && spanRow.timestamp
          ? Math.max(
              ((spanRow.timestamp + spanRow.duration - minTimestamp) /
                (maxTimestamp - minTimestamp)) *
                100 -
                left,
              0.1,
            )
          : 0.1,
      ),
    [left, maxTimestamp, minTimestamp, spanRow.duration, spanRow.timestamp],
  );

  return (
    <rect
      x={`${left}%`}
      y={`${top}%`}
      width={`${width}%`}
      height="3"
      fill={selectServiceColor(spanRow.serviceName)}
    />
  );
};
