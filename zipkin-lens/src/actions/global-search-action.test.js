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
import * as actions from './global-search-action';
import * as types from '../constants/action-types';

describe('global search actions', () => {
  it('should create an action to set the lookback condition', () => {
    const lookbackCondition = {
      value: '1h',
      endTs: 1,
      startTs: 1,
    };
    const expectedAction = {
      type: types.GLOBAL_SEARCH_SET_LOOKBACK_CONDITION,
      lookbackCondition,
    };
    expect(actions.setLookbackCondition(lookbackCondition)).toEqual(expectedAction);
  });

  it('should create an action to set the limit condition', () => {
    const limitCondition = 10;
    const expectedAction = {
      type: types.GLOBAL_SEARCH_SET_LIMIT_CONDITION,
      limitCondition,
    };
    expect(actions.setLimitCondition(limitCondition)).toEqual(expectedAction);
  });

  it('should create an action to add a new condition', () => {
    const newCondition = {
      key: 'serviceName',
      value: 'serviceA',
    };
    const expectedAction = {
      type: types.GLOBAL_SEARCH_ADD_CONDITION,
      condition: newCondition,
    };
    expect(actions.addCondition(newCondition)).toEqual(expectedAction);
  });

  it('should create an action to delete conditions', () => {
    const index = 10;
    const expectedAction = {
      type: types.GLOBAL_SEARCH_DELETE_CONDITION,
      index,
    };
    expect(actions.deleteCondition(index)).toEqual(expectedAction);
  });

  it('should create an action to change the key of conditions', () => {
    const index = 10;
    const conditionKey = 'serviceName';
    const expectedAction = {
      type: types.GLOBAL_SEARCH_CHANGE_CONDITION_KEY,
      index,
      conditionKey,
    };
    expect(actions.changeConditionKey(index, conditionKey)).toEqual(expectedAction);
  });

  it('should create an action to change the value of conditions', () => {
    const index = 10;
    const conditionValue = 'serviceA';
    const expectedAction = {
      type: types.GLOBAL_SEARCH_CHANGE_CONDITION_VALUE,
      index,
      conditionValue,
    };
    expect(actions.changeConditionValue(index, conditionValue)).toEqual(expectedAction);
  });
});
