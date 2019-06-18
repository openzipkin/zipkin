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
const buildOrderedConditionKeyOptions = autocompleteKeys => ([
  'serviceName',
  'spanName',
  'minDuration',
  'maxDuration',
  ...autocompleteKeys,
  'tags',
  'remoteServiceName',
]);

export const buildConditionKeyOptions = (currentConditionKey, conditions, autocompleteKeys) => {
  const existingConditions = {};

  conditions.forEach((condition) => {
    if (condition.key === 'tags') {
      return;
    }
    existingConditions[condition.key] = true;
  });

  return buildOrderedConditionKeyOptions(autocompleteKeys).map((conditionKeyOption) => {
    if (conditionKeyOption === currentConditionKey) {
      return { conditionKey: conditionKeyOption, isDisabled: false };
    }
    if (existingConditions[conditionKeyOption]) {
      return { conditionKey: conditionKeyOption, isDisabled: true };
    }
    return { conditionKey: conditionKeyOption, isDisabled: false };
  });
};

export const retrieveNextConditionKey = (conditions, autocompleteKeys) => {
  const conditionKeyOptions = buildOrderedConditionKeyOptions(autocompleteKeys);

  const existingConditions = {};
  conditions.forEach((condition) => {
    existingConditions[condition.key] = true;
  });

  for (let i = 0; i < conditionKeyOptions.length; i += 1) {
    const conditionKey = conditionKeyOptions[i];
    if (!existingConditions[conditionKey]) {
      return conditionKey;
    }
  }
  return 'tags';
};

export const retrieveDefaultConditionValue = (conditionKey) => {
  switch (conditionKey) {
    case 'serviceName':
      return undefined;
    case 'remoteServiceName':
      return undefined;
    case 'spanName':
      return undefined;
    case 'minDuration':
      return 10;
    case 'maxDuration':
      return 100;
    case 'tags':
      return '';
    default: // autocompleteKeys
      return undefined;
  }
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
