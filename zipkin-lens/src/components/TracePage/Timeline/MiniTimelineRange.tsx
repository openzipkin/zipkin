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

import { useTheme } from '@material-ui/core';
import React, {
  MouseEvent as ReactMouseEvent,
  MutableRefObject,
  useCallback,
  useMemo,
  useState,
} from 'react';

const calculateRelativeX = (parentRect: DOMRect, x: number) => {
  const value =
    ((x - parentRect.left) / (parentRect.right - parentRect.left)) * 100;
  if (value <= 0) {
    return 0;
  }
  if (value >= 100) {
    return 100;
  }
  return value;
};

const useRangeHandler = (
  rootEl: MutableRefObject<SVGSVGElement | null>,
  minTimestamp: number,
  maxTimestamp: number,
  setTimestamp: (value: number) => void,
) => {
  const [currentX, setCurrentX] = useState<number>();
  const [mouseDownX, setMouseDownX] = useState<number>();
  const [isDragging, setIsDragging] = useState(false);

  const onMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!rootEl.current) {
        return;
      }
      const x = calculateRelativeX(
        rootEl.current.getBoundingClientRect(),
        e.pageX,
      );
      setCurrentX(x);
    },
    [rootEl],
  );

  const onMouseUp = useCallback(
    (e: MouseEvent) => {
      if (!rootEl.current) {
        return;
      }
      const x = calculateRelativeX(
        rootEl.current.getBoundingClientRect(),
        e.pageX,
      );
      setTimestamp((x / 100) * (maxTimestamp - minTimestamp) + minTimestamp);
      setCurrentX(undefined);
      setMouseDownX(undefined);
      setIsDragging(false);

      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    },
    [maxTimestamp, minTimestamp, onMouseMove, rootEl, setTimestamp],
  );

  const onMouseDown = useCallback(
    (e: ReactMouseEvent<SVGRectElement>) => {
      if (!rootEl.current) {
        return;
      }
      const x = calculateRelativeX(
        rootEl.current.getBoundingClientRect(),
        e.pageX,
      );
      setCurrentX(x);
      setMouseDownX(x);
      setIsDragging(true);

      window.addEventListener('mousemove', onMouseMove);
      window.addEventListener('mouseup', onMouseUp);
    },
    [onMouseMove, onMouseUp, rootEl],
  );

  return { currentX, mouseDownX, onMouseDown, isDragging };
};

type MiniTimelineRangeProps = {
  rootEl: MutableRefObject<SVGSVGElement | null>;
  minTimestamp: number;
  maxTimestamp: number;
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
  setSelectedMinTimestamp: (value: number) => void;
  setSelectedMaxTimestamp: (value: number) => void;
};

export const MiniTimelineRange = ({
  rootEl,
  minTimestamp,
  maxTimestamp,
  selectedMinTimestamp,
  selectedMaxTimestamp,
  setSelectedMinTimestamp,
  setSelectedMaxTimestamp,
}: MiniTimelineRangeProps) => {
  const theme = useTheme();

  const minRangeHandler = useRangeHandler(
    rootEl,
    minTimestamp,
    maxTimestamp,
    setSelectedMinTimestamp,
  );

  const maxRangeHandler = useRangeHandler(
    rootEl,
    minTimestamp,
    maxTimestamp,
    setSelectedMaxTimestamp,
  );

  const rightOnTheLeft = useMemo(
    () =>
      ((selectedMinTimestamp - minTimestamp) / (maxTimestamp - minTimestamp)) *
      100,
    [maxTimestamp, minTimestamp, selectedMinTimestamp],
  );

  const leftOnTheRight = useMemo(
    () =>
      ((selectedMaxTimestamp - minTimestamp) / (maxTimestamp - minTimestamp)) *
      100,
    [maxTimestamp, minTimestamp, selectedMaxTimestamp],
  );

  return (
    <>
      <rect
        x="0"
        y="0"
        width={`${rightOnTheLeft}%`}
        height="100%"
        fill={theme.palette.grey[500]}
        fillOpacity="0.2"
      />
      <rect
        x={`${leftOnTheRight}%`}
        y="0"
        width={`${100 - leftOnTheRight}%`}
        height="100%"
        fill={theme.palette.grey[500]}
        fillOpacity="0.2"
      />
      <rect
        x={`${rightOnTheLeft}%`}
        y="0"
        width="2"
        height="100%"
        fill={theme.palette.divider}
        transform="translate(-1)"
      />
      <rect
        x={`${leftOnTheRight}%`}
        y="0"
        width="2"
        height="100%"
        fill={theme.palette.divider}
        transform="translate(-1)"
      />
      {minRangeHandler.mouseDownX !== undefined &&
        minRangeHandler.currentX !== undefined && (
          <rect
            x={`${Math.min(
              minRangeHandler.mouseDownX,
              minRangeHandler.currentX,
            )}%`}
            y="0"
            width={`${Math.abs(
              minRangeHandler.mouseDownX - minRangeHandler.currentX,
            )}%`}
            height="100%"
            fill={theme.palette.secondary.light}
            fillOpacity="0.2"
          />
        )}
      {maxRangeHandler.mouseDownX !== undefined &&
        maxRangeHandler.currentX !== undefined && (
          <rect
            x={`${Math.min(
              maxRangeHandler.mouseDownX,
              maxRangeHandler.currentX,
            )}%`}
            y="0"
            width={`${Math.abs(
              maxRangeHandler.mouseDownX - maxRangeHandler.currentX,
            )}%`}
            height="100%"
            fill={theme.palette.secondary.light}
            fillOpacity="0.2"
          />
        )}
      <rect
        x={`${rightOnTheLeft}%`}
        y="0"
        width="6"
        height="40%"
        fill={
          minRangeHandler.isDragging
            ? theme.palette.primary.dark
            : theme.palette.primary.main
        }
        onMouseDown={minRangeHandler.onMouseDown}
        cursor="pointer"
        transform="translate(-3)"
      />
      <rect
        x={`${leftOnTheRight}%`}
        y="0"
        width="6"
        height="40%"
        fill={
          maxRangeHandler.isDragging
            ? theme.palette.primary.dark
            : theme.palette.primary.main
        }
        onMouseDown={maxRangeHandler.onMouseDown}
        cursor="pointer"
        transform="translate(-3)"
      />
    </>
  );
};
