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
export const lookbackDurations = {
  '1m': 60000,
  '5m': 300000,
  '15m': 900000,
  '30m': 1800000,
  '1h': 3600000,
  '2h': 7200000,
  '6h': 21600000,
  '12h': 43200000,
  '1d': 86400000,
  '2d': 172800000,
  '7d': 604800000,
};

export const buildCommonQueryParameters = (
  conditions,
  lookbackCondition,
  limitCondition,
  currentTime,
) => {
  const conditionMap = {};
  const tagConditions = [];
  const autocompleteTagConditions = [];

  conditions.forEach((condition) => {
    switch (condition.key) {
      case 'serviceName':
      case 'remoteServiceName':
      case 'spanName':
      case 'minDuration':
      case 'maxDuration':
        conditionMap[condition.key] = condition.value;
        break;
      case 'tags':
        tagConditions.push(condition.value);
        break;
      default: // autocompleteTags
        autocompleteTagConditions.push(`${condition.key}=${condition.value}`);
        break;
    }
  });
  if (tagConditions.length > 0) {
    conditionMap.tags = tagConditions.join(' and ');
  }
  if (autocompleteTagConditions.length > 0) {
    conditionMap.autocompleteTags = autocompleteTagConditions.join(' and ');
  }
  conditionMap.limit = limitCondition;
  conditionMap.lookback = lookbackCondition.value;

  if (lookbackCondition.value === 'custom') {
    conditionMap.endTs = lookbackCondition.endTs;
    conditionMap.startTs = lookbackCondition.startTs;
  } else {
    conditionMap.endTs = currentTime.valueOf();
  }

  return conditionMap;
};

export const buildTracesApiQueryParameters = (
  conditions,
  lookbackCondition,
  limitCondition,
  currentTime,
) => {
  const conditionMap = {};
  const annotationQueryConditions = [];

  conditions.forEach((condition) => {
    switch (condition.key) {
      case 'serviceName':
      case 'remoteServiceName':
      case 'spanName':
      case 'minDuration':
      case 'maxDuration':
        conditionMap[condition.key] = condition.value;
        break;
      case 'tags':
        annotationQueryConditions.push(condition.value);
        break;
      default: // autocompleteTags
        annotationQueryConditions.push(`${condition.key}=${condition.value}`);
        break;
    }
  });
  if (annotationQueryConditions.length > 0) {
    conditionMap.annotationQuery = annotationQueryConditions.join(' and ');
  }

  conditionMap.limit = limitCondition;

  if (lookbackCondition.value === 'custom') {
    conditionMap.endTs = lookbackCondition.endTs;
    conditionMap.lookback = lookbackCondition.endTs - lookbackCondition.startTs;
  } else {
    conditionMap.endTs = currentTime.valueOf();
    conditionMap.lookback = lookbackDurations[lookbackCondition.value];
  }

  return conditionMap;
};

export const buildDependenciesApiQueryParameters = (
  lookbackCondition,
  currentTime,
) => {
  const conditionMap = {};
  if (lookbackCondition.value === 'custom') {
    conditionMap.endTs = lookbackCondition.endTs;
    conditionMap.lookback = lookbackCondition.endTs - lookbackCondition.startTs;
  } else {
    conditionMap.endTs = currentTime.valueOf();
    conditionMap.lookback = lookbackDurations[lookbackCondition.value];
  }
  return conditionMap;
};

export const extractConditionsFromQueryParameters = (queryParameters) => {
  const conditions = [];
  const lookbackCondition = {};
  let limitCondition = 0;

  Object.keys(queryParameters).forEach((conditionKey) => {
    const conditionValue = queryParameters[conditionKey];
    switch (conditionKey) {
      case 'serviceName':
      case 'remoteServiceName':
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
      case 'tags':
        conditionValue.split(' and ').forEach((tags) => {
          conditions.push({
            key: 'tags',
            value: tags,
          });
        });
        break;
      case 'autocompleteTags':
        conditionValue.split(' and ').forEach((autocompleteTag) => {
          const splitted = autocompleteTag.split('=');
          conditions.push({
            key: splitted[0],
            value: splitted[1],
          });
        });
        break;
      case 'limit':
        limitCondition = parseInt(conditionValue, 10);
        break;
      case 'lookback':
        switch (conditionValue) {
          case '1m':
          case '5m':
          case '15m':
          case '30m':
          case '1h':
          case '2h':
          case '6h':
          case '12h':
          case '1d':
          case '2d':
          case '7d': {
            lookbackCondition.value = conditionValue;
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
