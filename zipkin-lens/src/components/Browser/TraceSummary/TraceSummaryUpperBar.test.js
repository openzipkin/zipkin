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
import moment from 'moment';

import TraceSummaryUpperBar from './TraceSummaryUpperBar';

describe('<TraceSummaryUpperBar />', () => {
  it('should render component correctly', () => {
    const traceSummary = {
      traceId: '1',
      width: 10,
      infoClass: '',
      duration: 3,
      durationStr: '3μs',
      spanCount: 10,
      timestamp: 1555936837862000,
      serviceSummaries: [],
    };
    const wrapper = shallow(<TraceSummaryUpperBar traceSummary={traceSummary} />);
    expect(wrapper.find('[data-test="duration"]').text()).toEqual('3μs');
    expect(wrapper.find('[data-test="spans"]').text()).toEqual('10 spans');
    expect(wrapper.find('[data-test="timestamp"]').text()).toEqual(
      moment(1555936837862).format('MM/DD HH:mm:ss:SSS'),
    );
  });
});
