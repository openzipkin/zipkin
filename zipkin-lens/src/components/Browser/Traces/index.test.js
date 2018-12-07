/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import React from 'react';
import { shallow } from 'enzyme';

import Traces from './index';
import InitialMessage from './InitialMessage';

describe('<Traces>', () => {
  it('should show InitialMessage if location.search is empty', () => {
    const props = {
      isLoading: false,
      clockSkewCorrectedTracesMap: {},
      traceSummaries: [],
      location: {
        search: '',
      },
    };
    const wrapper = shallow(<Traces {...props} />);
    expect(wrapper.find(InitialMessage).length).toBe(1);
    expect(wrapper.find('.traces').length).toBe(0);
  });

  it('should show Traces if location.search is not empty', () => {
    const props = {
      isLoading: false,
      clockSkewCorrectedTracesMap: {},
      traceSummaries: [],
      location: {
        search: '?serviceName=serviceA',
      },
    };
    const wrapper = shallow(<Traces {...props} />);
    expect(wrapper.find(InitialMessage).length).toBe(0);
    expect(wrapper.find('.traces').length).toBe(1);
  });
});
