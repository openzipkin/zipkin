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
  limitCondition: '10',
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
