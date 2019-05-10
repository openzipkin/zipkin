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

import Browser from './Browser';
import BrowserHeader from './BrowserHeader';
import { sortingMethods } from './sorting';

describe('<Browser />', () => {
  const defaultProps = {
    location: {
      search: '',
    },
    clearTraces: jest.fn(),
    fetchTraces: () => {},
    traceSummaries: [],
    tracesMap: {},
    isLoading: false,
  };

  it('should clear traces when unmounted', () => {
    const wrapper = shallow(<Browser {...defaultProps} />);
    wrapper.unmount();
    const { clearTraces } = defaultProps;
    expect(clearTraces.mock.calls.length).toBe(1);
  });

  it('should change state when sorting method is changed', () => {
    const wrapper = shallow(<Browser {...defaultProps} />);
    wrapper.find(BrowserHeader).prop('onChange')({
      value: sortingMethods.SHORTEST,
    });
    expect(wrapper.state('sortingMethod')).toEqual(sortingMethods.SHORTEST);
  });
});
