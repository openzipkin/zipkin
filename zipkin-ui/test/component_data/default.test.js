/*
 * Copyright 2015-2017 The OpenZipkin Authors
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
import {convertToApiQuery} from '../../js/component_data/default';

describe('convertToApiQuery', () => {
  const should = require('chai').should();

  it('should not require startTs', () => {
    const parsed = convertToApiQuery('?endTs=1459169770000');

    parsed.endTs.should.equal('1459169770000');
    should.not.exist(parsed.lookback);
    should.not.exist(parsed.startTs);
  });

  it('should replace startTs with lookback', () => {
    const parsed = convertToApiQuery('?startTs=1459169760000&endTs=1459169770000');

    parsed.endTs.should.equal('1459169770000');
    parsed.lookback.should.equal('10000');
    should.not.exist(parsed.startTs);
  });

  it('should not add negative lookback', () => {
    const parsed = convertToApiQuery('?endTs=1459169760000&startTs=1459169770000');

    should.not.exist(parsed.lookback);
    should.not.exist(parsed.startTs);
  });
});
