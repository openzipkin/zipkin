/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { it, expect } from 'vitest';
import { getNodesAndEdges } from './DependenciesGraph';

//     10:3           20:7
// A ----------> B ----------> E
// |                           ^
// |                           |
// +------> C ------> D -------+
//   12:1       7:0       3:1
it('getNodesAndEdges', () => {
  const dependencies = [
    {
      parent: 'A',
      child: 'B',
      callCount: 10,
      errorCount: 3,
    },
    {
      parent: 'B',
      child: 'E',
      callCount: 20,
      errorCount: 7,
    },
    {
      parent: 'A',
      child: 'C',
      callCount: 12,
      errorCount: 1,
    },
    {
      parent: 'C',
      child: 'D',
      callCount: 7,
      errorCount: 0,
    },
    {
      parent: 'D',
      child: 'E',
      callCount: 3,
      errorCount: 1,
    },
  ];

  const { nodes, edges } = getNodesAndEdges(dependencies);

  const nodeNames = nodes.map((node) => node.name);
  expect(['A', 'B', 'C', 'D', 'E']).toEqual(expect.arrayContaining(nodeNames));

  expect(edges[0]).toEqual({
    source: 'A',
    target: 'B',
    metrics: {
      normal: 10,
      danger: 3,
    },
  });
});
