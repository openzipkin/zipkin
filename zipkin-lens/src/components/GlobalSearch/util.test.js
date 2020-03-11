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
import { buildConditionKeyOptions, retrieveNextConditionKey } from './util';

describe('retrieveNextConditionKey', () => {
  it('should return the "serviceName" when conditions is empty', () => {
    expect(retrieveNextConditionKey([], [])).toEqual('serviceName');
  });

  it('should return the unused condition key', () => {
    expect(retrieveNextConditionKey([
      { key: 'serviceName' },
      { key: 'remoteServiceName' },
      { key: 'spanName' },
      { key: 'maxDuration' },
    ], [])).toEqual('minDuration');
  });

  it('should return "tags" when all condition keys are used', () => {
    expect(retrieveNextConditionKey([
      { key: 'serviceName' },
      { key: 'remoteServiceName' },
      { key: 'spanName' },
      { key: 'minDuration' },
      { key: 'maxDuration' },
      { key: 'tags' },
    ], [])).toEqual('tags');
  });
});

describe('buildConditionKeyOptions', () => {
  it('should return the right availability', () => {
    const sorter = (a, b) => {
      const keyA = a.conditionKey.toUpperCase();
      const keyB = b.conditionKey.toUpperCase();
      if (keyA < keyB) {
        return -1;
      }
      if (keyA > keyB) {
        return 1;
      }
      return 0;
    };
    const result = buildConditionKeyOptions(
      'serviceName',
      [
        { key: 'maxDuration' },
        { key: 'tags' },
        { key: 'environment' },
      ],
      ['instanceId', 'environment'],
    );
    expect(result.sort(sorter)).toEqual([
      { conditionKey: 'serviceName', isDisabled: false },
      { conditionKey: 'remoteServiceName', isDisabled: false },
      { conditionKey: 'spanName', isDisabled: false },
      { conditionKey: 'minDuration', isDisabled: false },
      { conditionKey: 'maxDuration', isDisabled: true },
      { conditionKey: 'tags', isDisabled: false }, // always false
      { conditionKey: 'instanceId', isDisabled: false },
      { conditionKey: 'environment', isDisabled: true },
    ].sort(sorter));
  });
});
