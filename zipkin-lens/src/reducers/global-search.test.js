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
import reducer from './global-search';
import * as types from '../constants/action-types';

describe('global search reducer', () => {
  it('should handle GLOBAL_SEARCH_SET_LOOKBACK_CONDITION', () => {
    const newState = reducer(undefined, {
      type: types.GLOBAL_SEARCH_SET_LOOKBACK_CONDITION,
      lookbackCondition: {
        value: '1h',
        endTs: 1,
      },
    });
    expect(newState.lookbackCondition).toEqual({
      value: '1h',
      endTs: 1,
    });
  });

  it('should handle GLOBAL_SEARCH_SET_LIMIT_CONDITION', () => {
    const newState = reducer(undefined, {
      type: types.GLOBAL_SEARCH_SET_LIMIT_CONDITION,
      limitCondition: 25,
    });
    expect(newState.limitCondition).toEqual(25);
  });

  it('should handle GLOBAL_SEARCH_ADD_CONDITION', () => {
    const newState = reducer(
      { conditions: [{ key: 'serviceName', value: 'serviceA' }] },
      {
        type: types.GLOBAL_SEARCH_ADD_CONDITION,
        condition: {
          key: 'spanName',
          value: 'spanA',
        },
      },
    );
    expect(newState.conditions[0].key).toEqual('serviceName');
    expect(newState.conditions[0].value).toEqual('serviceA');
    expect(newState.conditions[1].key).toEqual('spanName');
    expect(newState.conditions[1].value).toEqual('spanA');
  });

  it('should handle GLOBAL_SEARCH_DELETE_CONDITION', () => {
    const newState = reducer(
      {
        conditions: [
          { key: 'serviceName', value: 'serviceA' },
          { key: 'spanName', value: 'spanA' },
          { key: 'minDuration', value: 100 },
        ],
      },
      { type: types.GLOBAL_SEARCH_DELETE_CONDITION, index: 1 },
    );
    expect(newState.conditions).toEqual([
      { key: 'serviceName', value: 'serviceA' },
      { key: 'minDuration', value: 100 },
    ]);
  });

  it('should handle GLOBAL_SEARCH_CHANGE_CONDITION_KEY', () => {
    const initialConditions = [
      { key: 'serviceName', value: 'serviceA' },
      { key: 'spanName', value: 'spanA' },
      { key: 'minDuration', value: 10 },
      { key: 'maxDuration', value: 100 },
    ];

    let state = reducer(
      { conditions: initialConditions },
      {
        type: types.GLOBAL_SEARCH_CHANGE_CONDITION_KEY,
        index: 1,
        conditionKey: 'tags',
      },
    );
    state = reducer(
      { conditions: state.conditions },
      {
        type: types.GLOBAL_SEARCH_CHANGE_CONDITION_KEY,
        index: 2,
        conditionKey: 'spanName',
      },
    );
    expect(state.conditions).toEqual([
      { key: 'serviceName', value: 'serviceA' },
      { key: 'tags', value: '' }, // changed
      { key: 'spanName', value: undefined }, // changed
      { key: 'maxDuration', value: 100 },
    ]);
  });

  it('should handle GLOBAL_SEARCH_CHANGE_CONDITION_VALUE', () => {
    const initialConditions = [
      { key: 'serviceName', value: 'serviceA' },
      { key: 'spanName', value: 'spanA' },
      { key: 'minDuration', value: 10 },
      { key: 'maxDuration', value: 100 },
    ];

    let state = reducer(
      { conditions: initialConditions },
      {
        type: types.GLOBAL_SEARCH_CHANGE_CONDITION_VALUE,
        index: 0,
        conditionValue: 'serviceB',
      },
    );
    state = reducer(
      { conditions: state.conditions },
      {
        type: types.GLOBAL_SEARCH_CHANGE_CONDITION_VALUE,
        index: 1,
        conditionValue: 'spanB',
      },
    );
    expect(state.conditions).toEqual([
      { key: 'serviceName', value: 'serviceB' }, // changed
      { key: 'spanName', value: 'spanB' }, // changed
      { key: 'minDuration', value: 10 },
      { key: 'maxDuration', value: 100 },
    ]);
  });
});
