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
import {
  sortTraceSummaries,
  sortingMethods,
  extractAllServiceNames,
  filterTraceSummaries,
} from './util';

describe('sortTraceSummaries', () => {
  const pairsToTraceSummaries = (pairs) =>
    pairs.map((pair) => ({ duration: pair[0], timestamp: pair[1] }));

  const input = pairsToTraceSummaries([
    [1, 3], // [duration, timestamp]
    [3, 2],
    [2, 1],
  ]);

  it('LONGEST_FIRST', () => {
    expect(sortTraceSummaries(input, sortingMethods.LONGEST_FIRST)).toEqual(
      pairsToTraceSummaries([
        [3, 2],
        [2, 1],
        [1, 3],
      ]),
    );
    // original traceSummaries should not be changed.
    expect(input).toEqual(
      pairsToTraceSummaries([
        [1, 3],
        [3, 2],
        [2, 1],
      ]),
    );
  });

  it('SHORTEST', () => {
    expect(sortTraceSummaries(input, sortingMethods.SHORTEST_FIRST)).toEqual(
      pairsToTraceSummaries([
        [1, 3],
        [2, 1],
        [3, 2],
      ]),
    );
  });

  it('NEWEST', () => {
    expect(sortTraceSummaries(input, sortingMethods.NEWEST_FIRST)).toEqual(
      pairsToTraceSummaries([
        [1, 3],
        [3, 2],
        [2, 1],
      ]),
    );
  });

  it('OLDEST', () => {
    expect(sortTraceSummaries(input, sortingMethods.OLDEST_FIRST)).toEqual(
      pairsToTraceSummaries([
        [2, 1],
        [3, 2],
        [1, 3],
      ]),
    );
  });

  it('otherwise', () => {
    expect(sortTraceSummaries(input, 'OTHERWISE')).toEqual(
      pairsToTraceSummaries([
        [1, 3],
        [3, 2],
        [2, 1],
      ]),
    );
  });
});

describe('extractAllServiceNames', () => {
  it('should return all service names', () => {
    expect(
      extractAllServiceNames([
        {
          serviceSummaries: [
            { serviceName: 'service-A' },
            { serviceName: 'service-B' },
          ],
        },
        {
          serviceSummaries: [
            { serviceName: 'service-C' },
            { serviceName: 'service-D' },
          ],
        },
        {
          serviceSummaries: [
            { serviceName: 'service-B' },
            { serviceName: 'service-E' },
            { serviceName: 'service-A' },
          ],
        },
      ]),
    ).toEqual([
      'service-A',
      'service-B',
      'service-C',
      'service-D',
      'service-E',
    ]);
  });
});

describe('filterTraceSummaries', () => {
  it('should filter trace summaries', () => {
    const traceSummaries = [
      {
        serviceSummaries: [
          { serviceName: 'service-A' },
          { serviceName: 'service-B' },
        ],
      },
      {
        serviceSummaries: [
          { serviceName: 'service-C' },
          { serviceName: 'service-D' },
        ],
      },
      {
        serviceSummaries: [
          { serviceName: 'service-B' },
          { serviceName: 'service-E' },
          { serviceName: 'service-A' },
        ],
      },
      {
        serviceSummaries: [
          { serviceName: 'service-F' },
          { serviceName: 'service-A' },
        ],
      },
      {
        serviceSummaries: [{ serviceName: 'service-G' }],
      },
      {
        serviceSummaries: [
          { serviceName: 'service-H' },
          { serviceName: 'service-B' },
          { serviceName: 'service-A' },
        ],
      },
    ];
    expect(
      filterTraceSummaries(traceSummaries, ['service-A', 'service-B']),
    ).toEqual([
      {
        serviceSummaries: [
          { serviceName: 'service-A' },
          { serviceName: 'service-B' },
        ],
      },
      {
        serviceSummaries: [
          { serviceName: 'service-B' },
          { serviceName: 'service-E' },
          { serviceName: 'service-A' },
        ],
      },
      {
        serviceSummaries: [
          { serviceName: 'service-H' },
          { serviceName: 'service-B' },
          { serviceName: 'service-A' },
        ],
      },
    ]);
  });
});
