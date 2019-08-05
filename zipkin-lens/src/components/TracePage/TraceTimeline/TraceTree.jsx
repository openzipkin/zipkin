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

import {
  spanDataRowLineHeight,
  spanBarRowLineHeight,
  spanBarHeight,
} from './constants';

const TraceTree = ({ spans, depth, width }) => {
  const widthPerDepth = width / (depth + 1);

  const result = [];

  const stack = [];

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

      result.push(
        <line
          stroke="#999"
          strokeWidth={1}
          strokeDasharray="12 2"
          x1={parent.depth * widthPerDepth}
          x2="100%"
          y1={`${spanBarRowPosY + spanBarHeight / 2}rem`}
          y2={`${spanBarRowPosY + spanBarHeight / 2}rem`}
        />,
      );
      continue;
    }

    if (stackTop.depth === currentSpan.depth) {
      stack.pop();
      const parent = stack[stack.length - 1];
      stack.push({ index: i, depth: currentSpan.depth });

      result.push(
        <line
          stroke="#999"
          strokeWidth={1}
          strokeDasharray="12 2"
          x1={parent.depth * widthPerDepth}
          x2="100%"
          y1={`${spanBarRowPosY + spanBarHeight / 2}rem`}
          y2={`${spanBarRowPosY + spanBarHeight / 2}rem`}
        />,
      );
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

      result.push(
        <line
          stroke="#999"
          strokeWidth={1}
          strokeDasharray="12 2"
          x1={parent.depth * widthPerDepth}
          x2="100%"
          y1={`${spanBarRowPosY + spanBarHeight / 2}rem`}
          y2={`${spanBarRowPosY + spanBarHeight / 2}rem`}
        />,
      );

      for (let j = 0; j < popped.length - 1; j += 1) {
        result.push(
          <line
            stroke="#999"
            strokeWidth={1}
            x1={popped[j + 1].depth * widthPerDepth}
            x2={popped[j + 1].depth * widthPerDepth}
            y1={`${popped[j].index * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15}rem`}
            y2={`${popped[j + 1].index * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15}rem`}
          />,
        );
      }
      continue;
    }
  }

  result.push(
    <line
      stroke="#999"
      strokeWidth={1}
      strokeDasharray="12 2"
      x1={widthPerDepth}
      x2="100%"
      y1={`${spanDataRowLineHeight + spanBarHeight / 2}rem`}
      y2={`${spanDataRowLineHeight + spanBarHeight / 2}rem`}
    />,
  );

  for (let j = 0; j < stack.length - 1; j += 1) {
    result.push(
      <line
        stroke="#999"
        strokeWidth={1}
        x1={stack[j].depth * widthPerDepth}
        x2={stack[j].depth * widthPerDepth}
        y1={`${stack[j].index * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15}rem`}
        y2={`${stack[j + 1].index * (spanDataRowLineHeight + spanBarRowLineHeight) + spanDataRowLineHeight * 1.15}rem`}
      />,
    );
  }

  return result;
};

export default TraceTree;
