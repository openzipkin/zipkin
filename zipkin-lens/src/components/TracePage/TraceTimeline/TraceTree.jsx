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
import { makeStyles } from '@material-ui/styles';
import grey from '@material-ui/core/colors/grey';

import {
  spanDataRowLineHeight,
  spanBarRowLineHeight,
  spanBarHeight,
  expandButtonLengthOfSide,
} from '../constants';

const useStyles = makeStyles({
  expandButton: {
    cursor: 'pointer',
    opacity: 0,
    backgroundColor: grey[300],
    '&:hover': {
      opacity: 0.1,
    },
  },
});

const TraceTree = ({
  spans,
  depth,
  width,
  closedSpans,
  onSpanToggleButtonClick,
}) => {
  const classes = useStyles();

  const widthPerDepth = width / (depth + 1);

  const stack = [];

  const linePositions = [];
  const buttonData = [];

  for (let i = 0; i < spans.length; i += 1) {
    const currentSpan = spans[i];

    const spanRowOffsetY = i * (spanDataRowLineHeight + spanBarRowLineHeight);
    const spanBarRowPosY = spanRowOffsetY + spanDataRowLineHeight;

    if (stack.length === 0) {
      stack.push({ index: i, depth: currentSpan.depth });
      continue;
    }

    const stackTop = stack[stack.length - 1];

    if (stackTop.depth < currentSpan.depth) {
      const parent = stackTop;
      stack.push({ index: i, depth: currentSpan.depth });

      linePositions.push({
        x1: parent.depth * widthPerDepth,
        x2: width,
        y1: spanBarRowPosY + spanBarHeight / 2,
        y2: spanBarRowPosY + spanBarHeight / 2,
      });

      if (closedSpans[currentSpan.spanId]) {
        buttonData.push({
          x: currentSpan.depth * widthPerDepth
            - expandButtonLengthOfSide / 2,
          y: i * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15
            - expandButtonLengthOfSide / 2,
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

      linePositions.push({
        x1: parent.depth * widthPerDepth,
        x2: width,
        y1: spanBarRowPosY + spanBarHeight / 2,
        y2: spanBarRowPosY + spanBarHeight / 2,
      });

      if (closedSpans[currentSpan.spanId]) {
        buttonData.push({
          x: currentSpan.depth * widthPerDepth
            - expandButtonLengthOfSide / 2,
          y: i * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15
            - expandButtonLengthOfSide / 2,
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

      linePositions.push({
        x1: parent.depth * widthPerDepth,
        x2: width,
        y1: spanBarRowPosY + spanBarHeight / 2,
        y2: spanBarRowPosY + spanBarHeight / 2,
      });

      if (closedSpans[currentSpan.spanId]) {
        buttonData.push({
          x: currentSpan.depth * widthPerDepth
            - expandButtonLengthOfSide / 2,
          y: i * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15
            - expandButtonLengthOfSide / 2,
          spanId: currentSpan.spanId,
          isClosed: true,
        });
      }

      for (let j = 0; j < popped.length - 1; j += 1) {
        linePositions.push({
          x1: popped[j + 1].depth * widthPerDepth,
          x2: popped[j + 1].depth * widthPerDepth,
          y1: popped[j].index
            * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15,
          y2: popped[j + 1].index
            * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15,
        });

        buttonData.push({
          x: popped[j + 1].depth * widthPerDepth
            - expandButtonLengthOfSide / 2,
          y: popped[j + 1].index
            * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15
            - expandButtonLengthOfSide / 2,
          spanId: spans[popped[j + 1].index].spanId,
        });
      }
      continue;
    }
  }

  linePositions.push({
    x1: widthPerDepth * 2,
    x2: width,
    y1: spanDataRowLineHeight + spanBarHeight / 2,
    y2: spanDataRowLineHeight + spanBarHeight / 2,
  });

  if (closedSpans[spans[0].spanId]) {
    buttonData.push({
      x: spans[0].depth * widthPerDepth - expandButtonLengthOfSide / 2,
      y: spanDataRowLineHeight * 1.15 - expandButtonLengthOfSide / 2,
      spanId: spans[0].spanId,
      isClosed: true,
    });
  }

  for (let j = 0; j < stack.length - 1; j += 1) {
    linePositions.push({
      x1: stack[j].depth * widthPerDepth,
      x2: stack[j].depth * widthPerDepth,
      y1: stack[j].index
        * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15,
      y2: stack[j + 1].index
        * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15,
    });

    buttonData.push({
      x: stack[j].depth * widthPerDepth
        - expandButtonLengthOfSide / 2,
      y: stack[j].index
        * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15
        - expandButtonLengthOfSide / 2,
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
      stroke="#bbb"
      strokeWidth={1}
      x1={x1}
      x2={x2}
      y1={y1}
      y2={y2}
    />
  )).forEach(line => result.push(line));

  buttonData.map(({
    x,
    y,
    spanId,
    isClosed,
  }) => (
    <g>
      <rect
        rx={2}
        ry={2}
        x={x}
        y={y}
        width={expandButtonLengthOfSide}
        height={expandButtonLengthOfSide}
        stroke="#555"
        strokeWidth={1}
        fill="#fff"
      />
      <line
        x1={x}
        x2={x + expandButtonLengthOfSide}
        y1={y + expandButtonLengthOfSide / 2}
        y2={y + expandButtonLengthOfSide / 2}
        width={expandButtonLengthOfSide}
        height={1}
        stroke="#555"
        strokeWidth={2}
      />
      {
        !isClosed ? (
          <line
            x1={x + expandButtonLengthOfSide / 2}
            x2={x + expandButtonLengthOfSide / 2}
            y1={y}
            y2={y + expandButtonLengthOfSide}
            width={1}
            height={expandButtonLengthOfSide}
            stroke="#555"
            strokeWidth={2}
          />
        ) : null
      }
      <rect
        rx={2}
        ry={2}
        x={x}
        y={y}
        width={expandButtonLengthOfSide}
        height={expandButtonLengthOfSide}
        onClick={() => onSpanToggleButtonClick(spanId)}
        className={classes.expandButton}
      />
    </g>
  )).forEach(button => result.push(button));

  return result;
};

export default TraceTree;
