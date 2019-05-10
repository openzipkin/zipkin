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

import MiniTimeline from './MiniTimeline';

describe('<MiniTimeline />', () => {
  const commonProps = {
    startTs: 0,
    endTs: 10,
    onStartAndEndTsChange: () => {},
  };

  const dummySpan = {
    spanId: '1',
    spanName: 'span',
    parentId: '2',
    childIds: [],
    serviceName: 'service',
    serviceNames: [],
    timestamp: 0,
    duration: 10,
    durationStr: '10μs',
    tags: [],
    annotations: [],
    errorType: 'none',
    depth: 1,
    width: 1,
    left: 1,
  };

  it('should return null if the number of spans is less than 2', () => {
    let props = {
      ...commonProps,
      traceSummary: {
        traceId: '12345',
        spans: [],
        serviceNameAndSpanCounts: [],
        duration: 10,
        durationStr: '10μs',
      },
    };
    let wrapper = shallow(<MiniTimeline {...props} />);
    expect(wrapper.type()).toEqual(null);

    props = {
      ...commonProps,
      traceSummary: {
        traceId: '12345',
        spans: [dummySpan],
        serviceNameAndSpanCounts: [],
        duration: 10,
        durationStr: '10μs',
      },
    };
    wrapper = shallow(<MiniTimeline {...props} />);
    expect(wrapper.type()).toEqual(null);
  });

  it('should return mini timeline otherwise', () => {
    const props = {
      ...commonProps,
      traceSummary: {
        traceId: '12345',
        spans: [dummySpan, dummySpan],
        serviceNameAndSpanCounts: [],
        duration: 10,
        durationStr: '10μs',
      },
    };
    const wrapper = shallow(<MiniTimeline {...props} />);
    expect(wrapper.find('.mini-timeline').length).toBe(1);
  });
});
