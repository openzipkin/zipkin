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

export const buildCommonQueryParameters = (
  conditions,
  lookbackCondition,
  limitCondition,
  currentTs = null,
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
  } else if (currentTs) {
    conditionMap.endTs = currentTs;
  } else {
    conditionMap.endTs = lookbackCondition.endTs;
  }

  return conditionMap;
};

export const buildTracesApiQueryParameters = (
  conditions,
  lookbackCondition,
  limitCondition,
  currentTs = null,
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
  } else if (currentTs) {
    conditionMap.endTs = currentTs;
    conditionMap.lookback = lookbackCondition.value;
  } else {
    conditionMap.endTs = lookbackCondition.endTs;
    conditionMap.lookback = lookbackCondition.value;
  }

  return conditionMap;
};

export const buildDependenciesApiQueryParameters = (
  lookbackCondition,
  currentTs = null,
) => {
  const conditionMap = {};
  if (lookbackCondition.value === 'custom') {
    conditionMap.endTs = lookbackCondition.endTs;
    conditionMap.lookback = lookbackCondition.endTs - lookbackCondition.startTs;
  } else if (currentTs) {
    conditionMap.endTs = currentTs;
    conditionMap.lookback = lookbackCondition.value;
  } else {
    conditionMap.endTs = lookbackCondition.endTs;
    conditionMap.lookback = lookbackCondition.value;
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
        if (conditionValue === 'custom') {
          lookbackCondition.value = 'custom';
          lookbackCondition.endTs = parseInt(queryParameters.endTs, 10);
          lookbackCondition.startTs = parseInt(queryParameters.startTs, 10);
        } else {
          lookbackCondition.value = parseInt(conditionValue, 10);
          lookbackCondition.endTs = parseInt(queryParameters.endTs, 10);
        }
        break;
      default:
        break;
    }
  });
  return { conditions, lookbackCondition, limitCondition };
};
