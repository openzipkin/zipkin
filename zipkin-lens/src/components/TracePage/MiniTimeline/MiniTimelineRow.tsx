/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
