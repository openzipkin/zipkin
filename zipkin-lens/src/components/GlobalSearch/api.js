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
import { buildQueryParameters } from '../../util/api';

export const lookbackDurations = {
  '1h': 3600000,
  '2h': 7200000,
  '6h': 21600000,
  '12h': 43200000,
  '1d': 86400000,
  '2d': 172800000,
  '7d': 604800000,
};

export const buildTracesQueryParameters = (
  conditions,
  lookbackCondition,
  limitCondition,
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
  conditionMap.tags = tagConditions.join(' and ');
  conditionMap.autocompleteTags = autocompleteTagConditions.join(' and ');
  conditionMap.limit = limitCondition;
  conditionMap.lookback = lookbackCondition.value;
  conditionMap.endTs = lookbackCondition.endTs;
  if (lookbackCondition.value === 'custom') {
    conditionMap.startTs = lookbackCondition.startTs;
  }

  return buildQueryParameters(conditionMap);
};

export const buildTracesApiQueryParameters = (
  conditions,
  lookbackCondition,
  limitCondition,
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
  conditionMap.annotationQuery = annotationQueryConditions.join(' and ');

  conditionMap.limit = limitCondition;

  conditionMap.endTs = lookbackCondition.endTs;
  if (lookbackCondition.value === 'custom') {
    conditionMap.lookback = lookbackCondition.endTs - lookbackCondition.startTs;
  } else {
    conditionMap.lookback = lookbackDurations[lookbackCondition.value];
  }

  return buildQueryParameters(conditionMap);
};
