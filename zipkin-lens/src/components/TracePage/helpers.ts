/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { AdjustedSpan } from '../../models/AdjustedTrace';
import { SpanRow, TreeEdgeShapeType } from './types';

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
    return !span.parentId || !spans.find((p) => p.spanId === span.parentId);
  });
  roots.forEach((root) => {
    delete unconsumedSpans[root.spanId];
  });

  function fn(span: AdjustedSpan, depth: number): SpanTreeNode {
    const childSpans = span.childIds
      .map((childId) => unconsumedSpans[childId])
      .filter((s) => !!s);
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

const spanTreeToSpans = (roots: SpanTreeNode[]) => {
  const spans: AdjustedSpan[] = [];

  function fn(node: SpanTreeNode) {
    spans.push(node);
    node.children?.forEach(fn);
  }
  roots.forEach(fn);

  return spans;
};

const extractPartialTree = (roots: SpanTreeNode[], rerootedSpanId: string) => {
  let isFinished = false;
  let partialTree: SpanTreeNode[] = [];
  let spans: AdjustedSpan[] = [];

  function findRerootedSpan(node: SpanTreeNode) {
    if (node.spanId === rerootedSpanId) {
      isFinished = true;
      partialTree = [node];
      spans = spanTreeToSpans(partialTree);
    } else {
      node.children?.forEach(findRerootedSpan);
    }
  }

  // eslint-disable-next-line no-restricted-syntax
  for (const root of roots) {
    findRerootedSpan(root);
    if (isFinished) {
      break;
    }
  }

  return {
    roots: partialTree,
    spans,
  };
};

export const convertSpanTreeToSpanRowsAndTimestamps = (
  roots: SpanTreeNode[],
  closedSpanIdMap: { [spanId: string]: boolean },
  rerootedSpanId?: string,
) => {
  // If rerootedSpanId is specified, calculate the partial tree.
  let partialRoots: SpanTreeNode[];
  if (rerootedSpanId) {
    const result = extractPartialTree(roots, rerootedSpanId);
    partialRoots = result.roots;
  } else {
    partialRoots = roots;
  }

  let minTimestamp = Number.MAX_SAFE_INTEGER;
  let maxTimestamp = Number.MIN_SAFE_INTEGER;

  const rows = partialRoots.flatMap((root) => {
    const spanRows: SpanRow[] = [];

    // This function creates and appends span rows recursively, and
    // returns the number of nodes included.
    function fn(
      index: number,
      siblings: SpanTreeNode[],
      isParentClosed: boolean,
      parentTreeEdgeShape?: TreeEdgeShapeType[],
    ): number {
      const node = siblings[index];
      const isClosed = closedSpanIdMap[node.spanId] || false;
      let treeEdgeShape: TreeEdgeShapeType[] = [];

      if (node.timestamp !== undefined) {
        minTimestamp = Math.min(minTimestamp, node.timestamp);
      }
      if (node.timestamp !== undefined && node.duration !== undefined) {
        maxTimestamp = Math.max(maxTimestamp, node.timestamp + node.duration);
      }

      let spanRowIndex: number | undefined;
      if (!isParentClosed) {
        // If parentTreeEdgeShape is undefined, initialize treeEdgeShape with '-'.
        if (!parentTreeEdgeShape) {
          for (let i = 0; i < root.maxDepth; i += 1) {
            treeEdgeShape[i] = '-';
          }
          // If the node has children, the first element will be 'B'.
          if (node.children) {
            treeEdgeShape[0] = 'B';
          }
        } else {
          const relativeDepth = node.depth - root.depth;
          treeEdgeShape = [...parentTreeEdgeShape];

          if (
            relativeDepth >= 2 &&
            parentTreeEdgeShape[relativeDepth - 2] === 'E'
          ) {
            treeEdgeShape[relativeDepth - 2] = '-';
          }

          if (relativeDepth >= 1) {
            if (index === siblings.length - 1) {
              treeEdgeShape[relativeDepth - 1] = 'E';
            } else {
              treeEdgeShape[relativeDepth - 1] = 'M';
            }
          }
          if (node.children) {
            treeEdgeShape[relativeDepth] = 'B';
          }
        }
        spanRowIndex =
          spanRows.push({
            ...node,
            treeEdgeShape,
            isClosed,
            isCollapsible: !!node.children,
            numOfChildren: 0,
          }) - 1;
      }

      let numOfChildren = 0;
      if (node.children) {
        for (let i = 0; i < node.children.length; i += 1) {
          numOfChildren += fn(
            i,
            node.children,
            isClosed || isParentClosed,
            treeEdgeShape,
          );
        }
      }

      if (spanRowIndex !== undefined) {
        spanRows[spanRowIndex].numOfChildren = numOfChildren;
      }

      return numOfChildren + 1;
    }
    fn(0, [root], false, undefined);

    return spanRows;
  });

  return {
    minTimestamp,
    maxTimestamp,
    spanRows: rows,
  };
};

export const adjustPercentValue = (value: number) => {
  if (value <= 0) {
    return 0;
  }
  if (value >= 100) {
    return 100;
  }
  return value;
};
