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
import moment from 'moment';
import shortid from 'shortid';

import * as types from '../constants/action-types';
import { defaultConditionValues } from '../util/global-search';

const initialState = {
  conditions: [],
  lookbackCondition: {
    value: '1h',
    endTs: moment().valueOf(),
  },
  limitCondition: 10,
};

const globalSearch = (state = initialState, action) => {
  switch (action.type) {
    case types.GLOBAL_SEARCH_SET_LOOKBACK_CONDITION:
      return {
        ...state,
        lookbackCondition: action.lookbackCondition,
      };
    case types.GLOBAL_SEARCH_SET_LIMIT_CONDITION:
      return {
        ...state,
        limitCondition: action.limitCondition,
      };
    case types.GLOBAL_SEARCH_ADD_CONDITION: {
      const newCondition = {
        key: action.condition.key,
        value: action.condition.value,
        _id: shortid.generate(),
      };
      return {
        ...state,
        conditions: [
          ...state.conditions,
          newCondition,
        ],
      };
    }
    case types.GLOBAL_SEARCH_DELETE_CONDITION: {
      const conditions = [...state.conditions];
      conditions.splice(action.index, 1);
      return {
        ...state,
        conditions,
      };
    }
    case types.GLOBAL_SEARCH_CHANGE_CONDITION_KEY: {
      const conditions = [...state.conditions];
      const condition = { ...conditions[action.index] };
      condition.key = action.conditionKey;
      condition.value = defaultConditionValues(action.conditionKey);
      conditions[action.index] = condition;
      return {
        ...state,
        conditions,
      };
    }
    case types.GLOBAL_SEARCH_CHANGE_CONDITION_VALUE: {
      const conditions = [...state.conditions];
      const condition = { ...conditions[action.index] };
      condition.value = action.conditionValue;
      conditions[action.index] = condition;
      return {
        ...state,
        conditions,
      };
    }
    default:
      return state;
  }
};

export default globalSearch;
