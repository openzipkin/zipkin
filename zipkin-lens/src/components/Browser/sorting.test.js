import { sortingMethods, sortTraceSummaries } from './sorting';

const numberArrayToTraceSummaries = (numArray, key) => numArray.map(
  n => ({ [key]: n }),
);

describe('sortTraceSummaries', () => {
  it('LONGEST', () => {
    const originalDurations = [1, 5, 3, 10, 7];
    const traceSummaries = numberArrayToTraceSummaries(originalDurations, 'duration');
    const output = sortTraceSummaries(traceSummaries, sortingMethods.LONGEST);
    const expectedDurations = [10, 7, 5, 3, 1];
    for (let i = 0; i < traceSummaries.length; i += 1) {
      expect(output[i].duration).toEqual(expectedDurations[i]);
    }
    // original traceSummaries should not be changed.
    for (let i = 0; i < traceSummaries.length; i += 1) {
      expect(traceSummaries[i].duration).toEqual(originalDurations[i]);
    }
  });

  it('SHORTEST', () => {
    const originalDurations = [1, 5, 3, 10, 7];
    const traceSummaries = numberArrayToTraceSummaries(originalDurations, 'duration');
    const output = sortTraceSummaries(traceSummaries, sortingMethods.SHORTEST);
    const expectedDurations = [1, 3, 5, 7, 10];
    for (let i = 0; i < traceSummaries.length; i += 1) {
      expect(output[i].duration).toEqual(expectedDurations[i]);
    }
  });

  it('NEWEST', () => {
    const originalTimestamps = [1, 5, 3, 10, 7];
    const traceSummaries = numberArrayToTraceSummaries(originalTimestamps, 'timestamp');
    const output = sortTraceSummaries(traceSummaries, sortingMethods.NEWEST);
    const expectedTimestamps = [10, 7, 5, 3, 1];
    for (let i = 0; i < traceSummaries.length; i += 1) {
      expect(output[i].timestamp).toEqual(expectedTimestamps[i]);
    }
  });

  it('OLDEST', () => {
    const originalTimestamps = [1, 5, 3, 10, 7];
    const traceSummaries = numberArrayToTraceSummaries(originalTimestamps, 'timestamp');
    const output = sortTraceSummaries(traceSummaries, sortingMethods.OLDEST);
    const expectedTimestamps = [1, 3, 5, 7, 10];
    for (let i = 0; i < traceSummaries.length; i += 1) {
      expect(output[i].timestamp).toEqual(expectedTimestamps[i]);
    }
  });

  it('otherwise', () => {
    const originalTimestamps = [1, 5, 3, 10, 7];
    const traceSummaries = numberArrayToTraceSummaries(originalTimestamps, 'timestamp');
    const output = sortTraceSummaries(traceSummaries, 'OHTERWISE');
    const expectedTimestamps = [1, 5, 3, 10, 7];
    for (let i = 0; i < traceSummaries.length; i += 1) {
      expect(output[i].timestamp).toEqual(expectedTimestamps[i]);
    }
  });
});
