/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { useTheme } from '@material-ui/core';
import React, {
  MouseEvent as ReactMouseEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

const calculateX = (parentRect: DOMRect, x: number) => {
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

type MiniTimelineOverlayProps = {
  minTimestamp: number;
  maxTimestamp: number;
  setSelectedMinTimestamp: (value: number) => void;
  setSelectedMaxTimestamp: (value: number) => void;
};

export const MiniTimelineOverlay = ({
  minTimestamp,
  maxTimestamp,
  setSelectedMinTimestamp,
  setSelectedMaxTimestamp,
}: MiniTimelineOverlayProps) => {
  const theme = useTheme();
  const rootEl = useRef<SVGRectElement | null>(null);
  const [currentX, setCurrentX] = useState<number>();
  const [mouseDownX, setMouseDownX] = useState<number>();
  const [hoverX, setHoverX] = useState<number>();

  const mouseDownXRef = useRef(mouseDownX);
  useEffect(() => {
    mouseDownXRef.current = mouseDownX;
  }, [mouseDownX]);

  const handleMouseMove = useCallback((e: MouseEvent) => {
    if (!rootEl.current) {
      return;
    }
    const x = calculateX(rootEl.current.getBoundingClientRect(), e.pageX);
    setCurrentX(x);
  }, []);

  const handleMouseUp = useCallback(
    (e: MouseEvent) => {
      if (!rootEl.current || mouseDownXRef.current === undefined) {
        return;
      }
      const x = calculateX(rootEl.current.getBoundingClientRect(), e.pageX);
      // Adjust to avoid overlapping minTimestamp and maxTimestamp;
      const adjustedX = Math.abs(x - mouseDownXRef.current) < 1 ? x + 1 : x;

      const t1 =
        (mouseDownXRef.current / 100) * (maxTimestamp - minTimestamp) +
        minTimestamp;
      const t2 =
        (adjustedX / 100) * (maxTimestamp - minTimestamp) + minTimestamp;
      const newMinTimestmap = Math.min(t1, t2);
      const newMaxTimestamp = Math.max(t1, t2);
      setSelectedMinTimestamp(newMinTimestmap);
      setSelectedMaxTimestamp(newMaxTimestamp);

      setCurrentX(undefined);
      setMouseDownX(undefined);

      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    },
    [
      maxTimestamp,
      minTimestamp,
      handleMouseMove,
      setSelectedMaxTimestamp,
      setSelectedMinTimestamp,
    ],
  );

  const handleMouseDown = useCallback(
    (e: ReactMouseEvent<SVGRectElement>) => {
      if (!rootEl.current) {
        return;
      }
      const x = calculateX(rootEl.current.getBoundingClientRect(), e.pageX);
      setCurrentX(x);
      setMouseDownX(x);

      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
    },
    [handleMouseMove, handleMouseUp],
  );

  const handleMouseHoverMove = useCallback(
    (e: ReactMouseEvent<SVGRectElement>) => {
      if (e.buttons !== 0 || !rootEl.current) {
        return;
      }
      const x = calculateX(rootEl.current.getBoundingClientRect(), e.pageX);
      setHoverX(x);
    },
    [],
  );

  const handleMouseHoverLeave = useCallback(() => {
    setHoverX(undefined);
  }, []);

  return (
    <g>
      {mouseDownX !== undefined && currentX !== undefined && (
        <rect
          x={`${Math.min(mouseDownX, currentX)}%`}
          y="0"
          width={`${Math.abs(mouseDownX - currentX)}%`}
          height="100%"
          fill={theme.palette.secondary.light}
          fillOpacity="0.2"
          pointerEvents="none"
        />
      )}
      <rect
        ref={rootEl}
        x="0"
        y="0"
        width="100%"
        height="100%"
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseHoverMove}
        onMouseLeave={handleMouseHoverLeave}
        fillOpacity="0"
        cursor="col-resize"
      />
      {hoverX && (
        <line
          x1={`${hoverX}%`}
          y1="0"
          x2={`${hoverX}%`}
          y2="100%"
          stroke={theme.palette.secondary.main}
          strokeWidth={1}
          pointerEvents="none"
        />
      )}
    </g>
  );
};
