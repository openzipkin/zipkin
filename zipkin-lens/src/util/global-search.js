import { buildQueryParameters } from './api';

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

export const buildQueryParametersWithConditions = (
  conditions, lookbackCondition, limitCondition,
) => {
  const annotationQueryConditions = [];
  const conditionMap = {};

  conditions.forEach((condition) => {
    if (condition.key === 'annotationQuery') {
      annotationQueryConditions.push(condition.value);
    } else {
      conditionMap[condition.key] = condition.value;
    }
  });
  conditionMap.annotationQuery = annotationQueryConditions.join(' and ');

  conditionMap.limit = limitCondition;
  conditionMap.lookback = lookbackCondition.value;
  conditionMap.endTs = lookbackCondition.endTs;
  if (lookbackCondition.value === 'custom') {
    conditionMap.startTs = lookbackCondition.startTs;
  }

  return buildQueryParameters(conditionMap);
};

// Build query parameters of api/v2/traces API with query parameters of the
// trace page URL.
export const buildApiQueryParameters = (queryParameters) => {
  const result = {};
  Object.keys(queryParameters).forEach((conditionKey) => {
    const conditionValue = queryParameters[conditionKey];
    switch (conditionKey) {
      case 'serviceName':
      case 'spanName':
      case 'minDuration':
      case 'maxDuration':
      case 'annotationQuery':
      case 'limit':
        result[conditionKey] = conditionValue;
        break;
      case 'lookback':
        switch (conditionValue) {
          case '1h':
          case '2h':
          case '6h':
          case '12h':
          case '1d':
          case '2d':
          case '7d':
            result.endTs = queryParameters.endTs;
            result.lookback = String(lookbackDurations[conditionValue]);
            break;
          case 'custom':
            result.endTs = queryParameters.endTs;
            result.lookback = String(
              parseInt(queryParameters.endTs, 10) - parseInt(queryParameters.startTs, 10),
            );
            break;
          default:
            break;
        }
        break;
      default:
        break;
    }
  });
  return result;
};

export const extractConditionsFromQueryParameters = (queryParameters) => {
  const conditions = [];
  const lookbackCondition = {};
  let limitCondition = 0;

  Object.keys(queryParameters).forEach((conditionKey) => {
    const conditionValue = queryParameters[conditionKey];
    switch (conditionKey) {
      case 'serviceName':
      case 'spanName':
        conditions.push({
          key: conditionKey,
          value: conditionValue,
        });
        break;
      case 'minDuration':
      case 'maxDuration':
        conditions.push({
          key: conditionKey,
          value: parseInt(conditionValue, 10),
        });
        break;
      case 'annotationQuery':
        conditionValue.split(' and ').forEach((annotationQuery) => {
          conditions.push({
            key: conditionKey,
            value: annotationQuery,
          });
        });
        break;
      case 'limit':
        limitCondition = parseInt(conditionValue, 10);
        break;
      case 'lookback':
        switch (conditionValue) {
          case '1h':
          case '2h':
          case '6h':
          case '12h':
          case '1d':
          case '2d':
          case '7d': {
            lookbackCondition.value = conditionValue;
            lookbackCondition.endTs = parseInt(queryParameters.endTs, 10);
            break;
          }
          case 'custom':
            lookbackCondition.value = conditionValue;
            lookbackCondition.endTs = parseInt(queryParameters.endTs, 10);
            lookbackCondition.startTs = parseInt(queryParameters.startTs, 10);
            break;
          default:
            break;
        }
        break;
      default:
        break;
    }
  });
  return { conditions, lookbackCondition, limitCondition };
};
