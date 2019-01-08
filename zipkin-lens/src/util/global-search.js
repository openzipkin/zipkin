export const orderedConditionKeyList = [
  'serviceName',
  'spanName',
  'minDuration',
  'maxDuration',
  'annotationQuery',
];

export const defaultConditionValues = {
  serviceName: 'all',
  spanName: 'all',
  minDuration: 10,
  maxDuration: 100,
  annotationQuery: 'error',
};

export const lookbackDurations = {
  '1h': 3600000,
  '2h': 7200000,
  '6h': 21600000,
  '12h': 43200000,
  '1d': 86400000,
  '2d': 172800000,
  '7d': 604800000,
};

// Returns a key of search condition to be generated next.
export const nextInitialConditionKey = (conditions) => {
  const existingConditionsMemo = {};
  conditions.forEach((condition) => {
    existingConditionsMemo[condition.key] = true;
  });

  for (let i = 0; i < orderedConditionKeyList.length; i += 1) {
    const conditionKey = orderedConditionKeyList[i];
    if (!existingConditionsMemo[conditionKey]) {
      return conditionKey;
    }
  }
  return 'annotationQuery';
};
