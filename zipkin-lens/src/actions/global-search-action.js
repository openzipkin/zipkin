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
