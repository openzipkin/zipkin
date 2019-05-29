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
export const buildReactSelectStyle = (value, options, isFocused) => {
  let maxLength = 0;
  options.forEach((opt) => {
    if (maxLength < opt.length) {
      maxLength = opt.length;
    }
  });
  return {
    control: provided => ({
      ...provided,
      width: isFocused
        ? `${8 * maxLength + 16}px`
        : `${(8 * value.length) + 16}px`,
    }),
    indicatorsContainer: () => ({
      display: 'none',
    }),
    menuPortal: base => ({
      ...base,
      zIndex: 9999,
      width: `${8 * maxLength + 16}px`,
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
      return { conditionKeyOption, isDisabled: false };
    }
    if (existingConditions[conditionKeyOption]) {
      return { conditionKeyOption, isDisabled: true };
    }
    return { conditionKeyOption, isDisabled: false };
  });
};
