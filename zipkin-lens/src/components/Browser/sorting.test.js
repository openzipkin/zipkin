import { sortingMethods, sortTraceSummaries } from './sorting';

const pairsToTraceSummaries = pairs => pairs.map(
  pair => ({ duration: pair[0], timestamp: pair[1] }),
);

describe('sortTraceSummaries', () => {
  const input = pairsToTraceSummaries([
    [1, 3], // [duration, timestamp]
    [3, 2],
    [2, 1],
  ]);

  it('LONGEST', () => {
    expect(sortTraceSummaries(input, sortingMethods.LONGEST)).toEqual(pairsToTraceSummaries([
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
    expect(sortTraceSummaries(input, sortingMethods.SHORTEST)).toEqual(pairsToTraceSummaries([
      [1, 3],
      [2, 1],
      [3, 2],
    ]));
  });

  it('NEWEST', () => {
    expect(sortTraceSummaries(input, sortingMethods.NEWEST)).toEqual(pairsToTraceSummaries([
      [1, 3],
      [3, 2],
      [2, 1],
    ]));
  });

  it('OLDEST', () => {
    expect(sortTraceSummaries(input, sortingMethods.OLDEST)).toEqual(pairsToTraceSummaries([
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
