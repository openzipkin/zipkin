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
import { sortTraceSummaries, sortingMethods } from './sorting';

const pairsToTraceSummaries = pairs => pairs.map(
  pair => ({ duration: pair[0], timestamp: pair[1] }),
);

describe('sortTraceSummaries', () => {
  const input = pairsToTraceSummaries([
    [1, 3], // [duration, timestamp]
    [3, 2],
    [2, 1],
  ]);

  it('LONGEST_FIRST', () => {
    expect(sortTraceSummaries(input, sortingMethods.LONGEST_FIRST)).toEqual(pairsToTraceSummaries([
      [3, 2],
      [2, 1],
      [1, 3],
    ]));
    // original traceSummaries should not be changed.
    expect(input).toEqual(pairsToTraceSummaries([
      [1, 3],
      [3, 2],
      [2, 1],
    ]));
  });

  it('SHORTEST', () => {
    expect(sortTraceSummaries(input, sortingMethods.SHORTEST_FIRST)).toEqual(pairsToTraceSummaries([
      [1, 3],
      [2, 1],
      [3, 2],
    ]));
  });

  it('NEWEST', () => {
    expect(sortTraceSummaries(input, sortingMethods.NEWEST_FIRST)).toEqual(pairsToTraceSummaries([
      [1, 3],
      [3, 2],
      [2, 1],
    ]));
  });

  it('OLDEST', () => {
    expect(sortTraceSummaries(input, sortingMethods.OLDEST_FIRST)).toEqual(pairsToTraceSummaries([
      [2, 1],
      [3, 2],
      [1, 3],
    ]));
  });

  it('otherwise', () => {
    expect(sortTraceSummaries(input, 'OTHERWISE')).toEqual(pairsToTraceSummaries([
      [1, 3],
      [3, 2],
      [2, 1],
    ]));
  });
});
