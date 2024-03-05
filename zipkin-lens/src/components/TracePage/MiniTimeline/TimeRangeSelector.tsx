/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { useTheme } from '@material-ui/core';
import React, {
  MouseEvent as ReactMouseEvent,
  MutableRefObject,
  useCallback,
  useMemo,
  useState,
} from 'react';
import { adjustPercentValue } from '../helpers';

const calculateX = (
  parentRect: DOMRect,
  x: number,
  opositeX: number,
  isSmallerThanOpositeX: boolean,
) => {
  let value =
    ((x - parentRect.left) / (parentRect.right - parentRect.left)) * 100;
  if (isSmallerThanOpositeX) {
    if (value >= opositeX) {
      value = opositeX - 1;
    }
  } else if (value <= opositeX) {
    value = opositeX + 1;
  }
  return adjustPercentValue(value);
};

const useRangeHandler = (
  rootEl: MutableRefObject<SVGSVGElement | null>,
  minTimestamp: number,
  maxTimestamp: number,
  opositeX: number,
  isSmallerThanOpositeX: boolean,
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
      const x = calculateX(
        rootEl.current.getBoundingClientRect(),
        e.pageX,
        opositeX,
        isSmallerThanOpositeX,
      );
      setCurrentX(x);
    },
    [isSmallerThanOpositeX, opositeX, rootEl],
  );

  const onMouseUp = useCallback(
    (e: MouseEvent) => {
      if (!rootEl.current) {
        return;
      }
      const x = calculateX(
        rootEl.current.getBoundingClientRect(),
        e.pageX,
        opositeX,
        isSmallerThanOpositeX,
      );
      setTimestamp((x / 100) * (maxTimestamp - minTimestamp) + minTimestamp);
      setCurrentX(undefined);
      setMouseDownX(undefined);
      setIsDragging(false);

      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    },
    [
      isSmallerThanOpositeX,
      maxTimestamp,
      minTimestamp,
      onMouseMove,
      opositeX,
      rootEl,
      setTimestamp,
    ],
  );

  const onMouseDown = useCallback(
    (e: ReactMouseEvent<SVGRectElement>) => {
      if (!rootEl.current) {
        return;
      }
      const x = calculateX(
        rootEl.current.getBoundingClientRect(),
        e.currentTarget.getBoundingClientRect().x + 3,
        opositeX,
        isSmallerThanOpositeX,
      );
      setCurrentX(x);
      setMouseDownX(x);
      setIsDragging(true);

      window.addEventListener('mousemove', onMouseMove);
      window.addEventListener('mouseup', onMouseUp);
    },
    [isSmallerThanOpositeX, onMouseMove, onMouseUp, opositeX, rootEl],
  );

  return { currentX, mouseDownX, onMouseDown, isDragging };
};

type TimeRangeSelectorProps = {
  rootEl: MutableRefObject<SVGSVGElement | null>;
  minTimestamp: number;
  maxTimestamp: number;
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
  setSelectedMinTimestamp: (value: number) => void;
  setSelectedMaxTimestamp: (value: number) => void;
};

export const TimeRangeSelector = ({
  rootEl,
  minTimestamp,
  maxTimestamp,
  selectedMinTimestamp,
  selectedMaxTimestamp,
  setSelectedMinTimestamp,
  setSelectedMaxTimestamp,
}: TimeRangeSelectorProps) => {
  const theme = useTheme();

  const minRangeHandler = useRangeHandler(
    rootEl,
    minTimestamp,
    maxTimestamp,
    ((selectedMaxTimestamp - minTimestamp) / (maxTimestamp - minTimestamp)) *
      100,
    true,
    setSelectedMinTimestamp,
  );

  const maxRangeHandler = useRangeHandler(
    rootEl,
    minTimestamp,
    maxTimestamp,
    ((selectedMinTimestamp - minTimestamp) / (maxTimestamp - minTimestamp)) *
      100,
    false,
    setSelectedMaxTimestamp,
  );

  const rightOnTheLeft = useMemo(
    () =>
      adjustPercentValue(
        ((selectedMinTimestamp - minTimestamp) /
          (maxTimestamp - minTimestamp)) *
          100,
      ),
    [maxTimestamp, minTimestamp, selectedMinTimestamp],
  );

  const leftOnTheRight = useMemo(
    () =>
      adjustPercentValue(
        ((selectedMaxTimestamp - minTimestamp) /
          (maxTimestamp - minTimestamp)) *
          100,
      ),
    [maxTimestamp, minTimestamp, selectedMaxTimestamp],
  );

  return (
    <g>
      <rect
        x="0"
        y="0"
        width={`${rightOnTheLeft}%`}
        height="100%"
        fill={theme.palette.grey[500]}
        fillOpacity="0.2"
        pointerEvents="none"
      />
      <rect
        x={`${leftOnTheRight}%`}
        y="0"
        width={`${100 - leftOnTheRight}%`}
        height="100%"
        fill={theme.palette.grey[500]}
        fillOpacity="0.2"
        pointerEvents="none"
      />
      <rect
        x={`${rightOnTheLeft}%`}
        y="0"
        width="2"
        height="100%"
        fill={theme.palette.divider}
        transform="translate(-1)"
        pointerEvents="none"
      />
      <rect
        x={`${leftOnTheRight}%`}
        y="0"
        width="2"
        height="100%"
        fill={theme.palette.divider}
        transform="translate(-1)"
        pointerEvents="none"
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
            pointerEvents="none"
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
            pointerEvents="none"
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
    </g>
  );
};
