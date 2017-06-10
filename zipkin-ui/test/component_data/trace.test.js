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
import {toContextualLogsUrl} from '../../js/component_data/trace';

describe('toContextualLogsUrl', () => {
  it('replaces token in logsUrl when set', () => {
    const kibanaLogsUrl = 'http://company.com/kibana/#/discover?_a=(query:(query_string:(query:\'{traceId}\')))';
    const traceId = '86bad84b319c8379';
    toContextualLogsUrl(kibanaLogsUrl, traceId)
      .should.equal(kibanaLogsUrl.replace('{traceId}', traceId));
  });

  it('returns logsUrl when not set', () => {
    const kibanaLogsUrl = undefined;
    const traceId = '86bad84b319c8379';
    (typeof toContextualLogsUrl(kibanaLogsUrl, traceId)).should.equal('undefined');
  });

  it('returns the same url when token not present', () => {
    const kibanaLogsUrl = 'http://mylogqueryservice.com/';
    const traceId = '86bad84b319c8379';
    toContextualLogsUrl(kibanaLogsUrl, traceId).should.equal(kibanaLogsUrl);
  });
});
