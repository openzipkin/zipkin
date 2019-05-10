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
import React from 'react';
import { shallow } from 'enzyme';

import TraceSummary from './TraceSummary';

jest.mock('../../../zipkin', () => ({
  ...(jest.requireActual('../../../zipkin')),
  detailedTraceSummary: () => ({
    traceId: '1',
    spans: [],
    serviceNameAndSpanCounts: [],
    duration: 10,
    durationStr: '10μs',
  }),
}));

describe('<TraceSummary />', () => {
  const defaultProps = {
    traceSummary: {
      traceId: '1',
      duration: 1,
      durationStr: '1μs',
      timestamp: 1,
      serviceSummaries: [{
        serviceName: 'serviceA',
        spanCount: 10,
      }],
      spanCount: 0,
      width: 1,
      infoClass: '',
    },
    skewCorrectedTrace: {},
  };
  it('should change state when clicked', () => {
    const wrapper = shallow(<TraceSummary {...defaultProps} />);
    const event = { stopPropagation: () => {} };
    wrapper.find('[data-test="summary"]').simulate('click', event);
    expect(wrapper.state().isTimelineOpened).toEqual(true);
    wrapper.find('[data-test="summary"]').simulate('click', event);
    expect(wrapper.state().isTimelineOpened).toEqual(false);
  });
});
