/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
