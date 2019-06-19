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
      return '';
    case 'remoteServiceName':
      return '';
    case 'spanName':
      return undefined;
    case 'minDuration':
      return 0;
    case 'maxDuration':
      return 0;
    case 'tags':
      return '';
    default: // autocompleteKeys
      return undefined;
  }
};
