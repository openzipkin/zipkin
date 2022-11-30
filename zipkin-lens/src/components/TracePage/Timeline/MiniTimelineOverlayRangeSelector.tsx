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
  useCallback,
  useEffect,
  useRef,
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

type MiniTimelineOverlayRangeSelectorProps = {
  minTimestamp: number;
  maxTimestamp: number;
  setSelectedMinTimestamp: (value: number) => void;
  setSelectedMaxTimestamp: (value: number) => void;
};

export const MiniTimelineOverlayRangeSelector = ({
  minTimestamp,
  maxTimestamp,
  setSelectedMinTimestamp,
  setSelectedMaxTimestamp,
}: MiniTimelineOverlayRangeSelectorProps) => {
  const theme = useTheme();
  const rootEl = useRef<SVGRectElement | null>(null);
  const [currentX, setCurrentX] = useState<number>();
  const [mouseDownX, setMouseDownX] = useState<number>();

  const mouseDownXRef = useRef(mouseDownX);
  useEffect(() => {
    mouseDownXRef.current = mouseDownX;
  }, [mouseDownX]);

  const onMouseMove = useCallback((e: MouseEvent) => {
    if (!rootEl.current) {
      return;
    }
    const x = calculateRelativeX(
      rootEl.current.getBoundingClientRect(),
      e.pageX,
    );
    setCurrentX(x);
  }, []);

  const onMouseUp = useCallback(
    (e: MouseEvent) => {
      console.log('here', rootEl.current, mouseDownX);
      if (!rootEl.current || mouseDownXRef.current === undefined) {
        return;
      }
      console.log('kokoko');
      const x = calculateRelativeX(
        rootEl.current.getBoundingClientRect(),
        e.pageX,
      );
      const t1 =
        (mouseDownXRef.current / 100) * (maxTimestamp - minTimestamp) +
        minTimestamp;
      const t2 = (x / 100) * (maxTimestamp - minTimestamp) + minTimestamp;
      const newMinTimestmap = Math.min(t1, t2);
      const newMaxTimestamp = Math.max(t1, t2);
      setSelectedMinTimestamp(newMinTimestmap);
      setSelectedMaxTimestamp(newMaxTimestamp);

      setCurrentX(undefined);
      setMouseDownX(undefined);

      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    },
    [
      maxTimestamp,
      minTimestamp,
      mouseDownX,
      onMouseMove,
      setSelectedMaxTimestamp,
      setSelectedMinTimestamp,
    ],
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

      window.addEventListener('mousemove', onMouseMove);
      window.addEventListener('mouseup', onMouseUp);
    },
    [onMouseMove, onMouseUp],
  );

  return (
    <>
      {mouseDownX !== undefined && currentX !== undefined && (
        <rect
          x={`${Math.min(mouseDownX, currentX)}%`}
          y="0"
          width={`${Math.abs(mouseDownX - currentX)}%`}
          height="100%"
          fill={theme.palette.secondary.light}
          fillOpacity="0.2"
        />
      )}
      <rect
        ref={rootEl}
        x="0"
        y="0"
        width="100%"
        height="100%"
        onMouseDown={onMouseDown}
        fillOpacity="0"
      />
    </>
  );
};
