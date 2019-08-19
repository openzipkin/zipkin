/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import React from 'react';
import { withStyles } from '@material-ui/styles';
import grey from '@material-ui/core/colors/grey';

import {
  spanBarLinePosY,
  spanToggleButtonTranslate,
  spanTreeLineWidthPercentPerDepth,
  spanTreeWidthPercent,
  spanToggleButtonLengthOfSide,
} from '../sizing';

const style = theme => ({
  line: {
    stroke: theme.palette.grey[500],
    strokeWidth: '1px',
  },
  spanToggleButton: {
    cursor: 'pointer',
    opacity: 0,
    backgroundColor: grey[300],
    '&:hover': {
      opacity: 0.1,
    },
  },
  buttonIcon: {
    stroke: theme.palette.grey[800],
    strokeWidth: 2,
  },
  spanToggleButtonInternal: {
    stroke: theme.palette.grey[700],
    strokeWidth: 1,
    fill: theme.palette.common.white,
  },
});

const TraceTree = ({
  spans,
  depth,
  closedSpans,
  onSpanToggleButtonClick,
  classes,
}) => {
  const stack = [];

  const linePositions = [];
  const buttonData = [];

  for (let i = 0; i < spans.length; i += 1) {
    const currentSpan = spans[i];

    if (stack.length === 0) {
      stack.push({ index: i, depth: currentSpan.depth });
      continue;
    }

    const stackTop = stack[stack.length - 1];

    if (stackTop.depth < currentSpan.depth) {
      const parent = stackTop;
      stack.push({ index: i, depth: currentSpan.depth });

      // Horizontal line
      linePositions.push({
        x1: `${parent.depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
        x2: `${spanTreeWidthPercent}%`,
        y1: spanBarLinePosY(i),
        y2: spanBarLinePosY(i),
      });

      if (closedSpans[currentSpan.spanId]) {
        buttonData.push({
          x: `${currentSpan.depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
          y: spanBarLinePosY(i),
          spanId: currentSpan.spanId,
          isClosed: true,
        });
      }
      continue;
    }

    if (stackTop.depth === currentSpan.depth) {
      stack.pop();
      const parent = stack[stack.length - 1];
      stack.push({ index: i, depth: currentSpan.depth });

      // Horizontal Line
      linePositions.push({
        x1: `${parent.depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
        x2: `${spanTreeWidthPercent}%`,
        y1: spanBarLinePosY(i),
        y2: spanBarLinePosY(i),
      });

      if (closedSpans[currentSpan.spanId]) {
        buttonData.push({
          x: `${currentSpan.depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
          y: spanBarLinePosY(i),
          spanId: currentSpan.spanId,
          isClosed: true,
        });
      }
      continue;
    }

    if (stackTop.depth > currentSpan.depth) {
      const popped = [];
      for (let j = stack.length - 1; j >= 0; j -= 1) {
        if (stack[j].depth < currentSpan.depth) {
          break;
        }
        popped.push(stack.pop());
      }
      const parent = stack[stack.length - 1];
      stack.push({ index: i, depth: currentSpan.depth });

      // Horizontal line
      linePositions.push({
        x1: `${parent.depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
        x2: `${spanTreeWidthPercent}%`,
        y1: spanBarLinePosY(i),
        y2: spanBarLinePosY(i),
      });

      if (closedSpans[currentSpan.spanId]) {
        buttonData.push({
          x: `${currentSpan.depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
          y: spanBarLinePosY(i),
          spanId: currentSpan.spanId,
          isClosed: true,
        });
      }

      for (let j = 0; j < popped.length - 1; j += 1) {
        // Vertical line
        linePositions.push({
          x1: `${popped[j + 1].depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
          x2: `${popped[j + 1].depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
          y1: spanBarLinePosY(popped[j].index),
          y2: spanBarLinePosY(popped[j + 1].index),
        });

        buttonData.push({
          x: `${popped[j + 1].depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
          y: spanBarLinePosY(popped[j + 1].index),
          spanId: spans[popped[j + 1].index].spanId,
        });
      }
      continue;
    }
  }

  // Horizontal line
  linePositions.push({
    x1: `${spanTreeLineWidthPercentPerDepth(depth) * 2}%`,
    x2: `${spanTreeWidthPercent}%`,
    y1: spanBarLinePosY(0),
    y2: spanBarLinePosY(0),
  });

  if (closedSpans[spans[0].spanId]) {
    buttonData.push({
      x: `${spans[0].depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
      y: spanBarLinePosY(0),
      spanId: spans[0].spanId,
      isClosed: true,
    });
  }

  for (let j = 0; j < stack.length - 1; j += 1) {
    // Vertical line
    linePositions.push({
      x1: `${stack[j].depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
      x2: `${stack[j].depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
      y1: spanBarLinePosY(stack[j].index),
      y2: spanBarLinePosY(stack[j + 1].index),
    });

    buttonData.push({
      x: `${stack[j].depth * spanTreeLineWidthPercentPerDepth(depth)}%`,
      y: spanBarLinePosY(stack[j].index),
      spanId: spans[stack[j].index].spanId,
    });
  }

  const result = [];

  linePositions.map(({
    x1,
    x2,
    y1,
    y2,
  }) => (
    <line
      key={`${x1}-${x2}-${y1}-${y2}`}
      x1={x1}
      x2={x2}
      y1={y1}
      y2={y2}
      className={classes.line}
    />
  )).forEach(line => result.push(line));

  buttonData.map(({
    x,
    y,
    spanId,
    isClosed,
  }) => (
    <g key={`${x}-${y}`} transform={spanToggleButtonTranslate}>
      <rect
        rx={2}
        ry={2}
        x={x}
        y={y}
        width={spanToggleButtonLengthOfSide}
        height={spanToggleButtonLengthOfSide}
        className={classes.spanToggleButtonInternal}
      />
      <svg x={x} y={y} className={classes.buttonIcon}>
        <line
          x1={0}
          x2={spanToggleButtonLengthOfSide}
          y1={spanToggleButtonLengthOfSide / 2}
          y2={spanToggleButtonLengthOfSide / 2}
        />
        {
          !isClosed ? (
            <line
              x1={spanToggleButtonLengthOfSide / 2}
              x2={spanToggleButtonLengthOfSide / 2}
              y1={0}
              y2={spanToggleButtonLengthOfSide}
            />
          ) : null
        }
      </svg>
      <rect
        rx={2}
        ry={2}
        x={x}
        y={y}
        width={spanToggleButtonLengthOfSide}
        height={spanToggleButtonLengthOfSide}
        onClick={() => onSpanToggleButtonClick(spanId)}
        className={classes.spanToggleButton}
      />

    </g>
  )).forEach(button => result.push(button));

  return result;
};

export default withStyles(style)(TraceTree);
