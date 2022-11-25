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

import { AdjustedSpan } from '../../../models/AdjustedTrace';
import { SpanRow, TreeEdgeShapeType } from '../types';

type SpanTreeNode = AdjustedSpan & {
  children?: SpanTreeNode[];
  maxDepth: number;
};

export const convertSpansToSpanTree = (
  spans: AdjustedSpan[],
): SpanTreeNode[] => {
  const idToSpan = spans.reduce<{ [id: string]: AdjustedSpan }>((acc, cur) => {
    acc[cur.spanId] = cur;
    return acc;
  }, {});
  const unconsumedSpans = { ...idToSpan };

  const roots = spans.filter((span) => {
    return !span.parentId || !idToSpan[span.parentId];
  });
  roots.forEach((root) => {
    delete unconsumedSpans[root.spanId];
  });

  function fn(span: AdjustedSpan, depth: number): SpanTreeNode {
    const childSpans = spans.filter(
      (s) => !!unconsumedSpans[s.spanId] && s.parentId === span.spanId,
    );
    childSpans.forEach((child) => {
      delete unconsumedSpans[child.spanId];
    });
    const children = childSpans.map((childSpan) => fn(childSpan, depth + 1));

    return {
      ...span,
      children: children.length > 0 ? children : undefined,
      depth,
      maxDepth:
        children.length > 0
          ? Math.max(...children.map((child) => child.maxDepth)) + 1
          : 0,
    };
  }

  return roots.map((root) => fn(root, 0));
};

/*
      0  1  2
      v  v  v
    0 +---------- SERVICE_A
      |
    1 |--+------- SERVICE_B
      |  |
    2 |  |--+---- SERVICE_C
      |  |  |
    3 |  |  |---- SERVICE_D
      |  |
    4 |  |--+---- SERVICE_E
      |     |
    5 |     |---- SERVICE_F
      |
    6 |---------- SERVICE_G 

  [
    [B, -, -], // SERVICE_A
    [M, B, -], // SERVICE_B
    [M, M, B], // SERVICE_C
    [M, M, E], // SERVICE_D
    [M, E, B], // SERVICE_E
    [M, -, E], // SERVICE_F
    [E, -, -], // SERVICE_G
  ]
*/
export const convertSpanTreeToSpanRow = (
  roots: SpanTreeNode[],
  spans: AdjustedSpan[],
) => {
  const minTimestamp = Math.min(
    ...spans.filter((span) => span.timestamp).map((span) => span.timestamp),
  );
  const maxTimestamp = Math.max(
    ...spans
      .filter((span) => span.timestamp && span.duration)
      .map((span) => span.timestamp + span.duration),
  );

  return roots.map((root) => {
    const spanRows: SpanRow[] = [];
    const openedDepth: boolean[] = [];
    for (let i = 0; i < root.maxDepth; i += 1) {
      openedDepth.push(false);
    }

    function fn(
      index: number,
      siblings: SpanTreeNode[],
      parentTreeEdgeShape?: TreeEdgeShapeType[],
    ) {
      const node = siblings[index];
      const left = node.timestamp
        ? ((node.timestamp - minTimestamp) / (maxTimestamp - minTimestamp)) *
          100
        : undefined;
      const width =
        left !== undefined && node.duration && node.timestamp
          ? ((node.timestamp + node.duration - minTimestamp) /
              (maxTimestamp - minTimestamp)) *
              100 -
            left
          : undefined;

      let treeEdgeShape: TreeEdgeShapeType[] = [];
      if (!parentTreeEdgeShape) {
        for (let i = 0; i < root.maxDepth; i += 1) {
          treeEdgeShape[i] = '-';
        }
        if (node.children) {
          treeEdgeShape[0] = 'B';
        }
      } else {
        treeEdgeShape = [...parentTreeEdgeShape];

        if (parentTreeEdgeShape[node.depth - 1] === 'E') {
          treeEdgeShape[node.depth - 1] = '-';
        } else {
          treeEdgeShape[node.depth - 1] = 'M';
        }

        if (node.children) {
          treeEdgeShape[node.depth] = 'B';
        } else if (index === siblings.length - 1) {
          treeEdgeShape[node.depth] = 'E';
        } else {
          treeEdgeShape[node.depth] = '-';
        }
      }

      spanRows.push({
        ...node,
        treeEdgeShape,
        left,
        width,
      });
      if (node.children) {
        for (let i = 0; i < node.children.length; i += 1) {
          fn(index, node.children, treeEdgeShape);
        }
      }
    }
    fn(0, [root], undefined);

    return spanRows;
  });
};

export const convertSpansToServiceTree = (spans: AdjustedSpan[]) => {};
