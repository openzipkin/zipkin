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

import Browser from './index';

describe('<Browser>', () => {
  it('should not fetch traces when location.search is empty', () => {
    const props = {
      location: {
        search: '',
      },
      clearTraces: () => {},
      fetchTraces: jest.fn(),
    };
    shallow(<Browser {...props} />);
    const { fetchTraces } = props;
    expect(fetchTraces.mock.calls.length).toBe(0);
  });

  it('should fetch traces when location.search is not empty', () => {
    const props = {
      location: {
        search: '?serviceName=serviceA',
      },
      clearTraces: () => {},
      fetchTraces: jest.fn(),
    };
    shallow(<Browser {...props} />);
    const { fetchTraces } = props;
    expect(fetchTraces.mock.calls.length).toBe(1);
  });

  it('should fetch traces when location is changed', () => {
    const props = {
      location: {
        search: '?serviceName=serviceA',
      },
      clearTraces: () => {},
      fetchTraces: jest.fn(),
    };
    const wrapper = shallow(<Browser {...props} />);
    wrapper.setProps({
      location: {
        search: '?serviceName=serviceB',
      },
    });
    const { fetchTraces } = props;
    expect(fetchTraces.mock.calls.length).toBe(2);
  });

  it('should clear traces when unmounted', () => {
    const props = {
      location: {
        search: '',
      },
      clearTraces: jest.fn(),
      fetchTraces: () => {},
    };
    const wrapper = shallow(<Browser {...props} />);
    wrapper.unmount();
    const { clearTraces } = props;
    expect(clearTraces.mock.calls.length).toBe(1);
  });
});
