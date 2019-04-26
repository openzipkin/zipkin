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
import * as types from '../constants/action-types';

export const setLookbackCondition = lookbackCondition => ({
  type: types.GLOBAL_SEARCH_SET_LOOKBACK_CONDITION,
  lookbackCondition,
});

export const setLimitCondition = limitCondition => ({
  type: types.GLOBAL_SEARCH_SET_LIMIT_CONDITION,
  limitCondition,
});

export const addCondition = (condition = null) => ({
  type: types.GLOBAL_SEARCH_ADD_CONDITION,
  condition,
});

export const deleteCondition = index => ({
  type: types.GLOBAL_SEARCH_DELETE_CONDITION,
  index,
});

export const changeConditionKey = (index, conditionKey) => ({
  type: types.GLOBAL_SEARCH_CHANGE_CONDITION_KEY,
  index,
  conditionKey,
});

export const changeConditionValue = (index, conditionValue) => ({
  type: types.GLOBAL_SEARCH_CHANGE_CONDITION_VALUE,
  index,
  conditionValue,
});
