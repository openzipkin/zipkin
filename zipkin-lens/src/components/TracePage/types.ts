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

import { AdjustedSpan } from '../../models/AdjustedTrace';

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
export type TreeEdgeShapeType = 'B' | 'M' | 'E' | '-';

export type SpanRow = AdjustedSpan & {
  treeEdgeShape: TreeEdgeShapeType[];
  isClosed: boolean;
  isCollapsible: boolean;
  numOfChildren: number;
};

export type ServiceTreeNode = {
  serviceName: string;
};

export type ServiceTreeEdge = {
  spans: AdjustedSpan[];
  sourceServiceName: string;
  targetServiceName: string;
  hasPair: boolean;
};
