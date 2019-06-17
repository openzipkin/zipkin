/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { theme } from '../../colors';

export const buildReactSelectStyle = (value, options, isFocused, baseSize, baseColor) => {
  const backgroundColor = theme.palette.primary[baseColor];
  let darkBackgroundColor;
  switch (baseColor) {
    case 'main':
      darkBackgroundColor = theme.palette.primary.dark;
      break;
    case 'light':
      darkBackgroundColor = theme.palette.primary.main;
      break;
    default:
      // Do nothing
  }

  return {
    control: base => ({
      ...base,
      width: isFocused
        ? `${baseSize * 1.5}rem`
        : `${baseSize}rem`,
      border: 0,
      borderRadius: 0,
      backgroundColor: isFocused ? darkBackgroundColor : backgroundColor,
      '&:hover': {
        backgroundColor: darkBackgroundColor,
      },
      cursor: 'pointer',
    }),
    menuPortal: base => ({
      ...base,
      zIndex: 10000,
      width: '14rem',
    }),
    singleValue: base => ({
      ...base,
      color: theme.palette.primary.contrastText,
    }),
    indicatorsContainer: base => ({
      ...base,
      display: 'none',
    }),
  };
};

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
