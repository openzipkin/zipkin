export const sortingMethods = {
  LONGEST: 'LONGEST',
  SHORTEST: 'SHORTEST',
  NEWEST: 'NEWEST',
  OLDEST: 'OLDEST',
};

export const sortingMethodOptions = [
  { value: sortingMethods.LONGEST, label: 'Longest First' },
  { value: sortingMethods.SHORTEST, label: 'Shortest First' },
  { value: sortingMethods.NEWEST, label: 'Newest First' },
  { value: sortingMethods.OLDEST, label: 'Oldest First' },
];

export const sortTraceSummaries = (traceSummaries, sortingMethod) => {
  const copied = [...traceSummaries];
  return copied.sort((a, b) => {
    switch (sortingMethod) {
      case sortingMethods.LONGEST:
        return b.duration - a.duration;
      case sortingMethods.SHORTEST:
        return a.duration - b.duration;
      case sortingMethods.NEWEST:
        return b.timestamp - a.timestamp;
      case sortingMethods.OLDEST:
        return a.timestamp - b.timestamp;
      default:
        return 0;
    }
  });
};
