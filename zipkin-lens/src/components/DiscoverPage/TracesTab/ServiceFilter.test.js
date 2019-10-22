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
import React from 'react';
import { mount } from 'enzyme';
import Popover from '@material-ui/core/Popover';

import ServiceFilter from './ServiceFilter';

describe('<ServiceFilter />', () => {
  let wrapper;

  const props = {
    filters: ['service-A', 'service-B'],
    allServiceNames: ['service-A', 'service-B', 'service-C', 'service-D'],
    onAddFilter: jest.fn(),
    onDeleteFilter: jest.fn(),
  };

  beforeEach(() => {
    wrapper = mount(
      <ServiceFilter {...props} />,
    );
  });

  it('should not show Popover when mounted', () => {
    const item = wrapper.find(Popover).first();
    expect(item.prop('open')).toBe(false);
  });

  it('should show Popover when the button is clicked', () => {
    const button = wrapper.find('[data-test="button"]').first();
    button.simulate('click');
    const item = wrapper.find(Popover).first();
    expect(item.prop('open')).toBe(true);
  });

  it('should hide badge if the number of filters is less than 2', () => {
    wrapper = mount(<ServiceFilter {...props} filters={['service-A']} />);
    expect(wrapper.find('[data-test="badge"]').first().prop('invisible')).toBe(true);

    wrapper = mount(<ServiceFilter {...props} filters={[]} />);
    expect(wrapper.find('[data-test="badge"]').first().prop('invisible')).toBe(true);
  });

  it('should show the badge content', () => {
    expect(wrapper.find('[data-test="badge"]').first().prop('badgeContent')).toBe('+1');
  });

  it('should not show a service name when there are not any filters', () => {
    wrapper = mount(<ServiceFilter {...props} filters={[]} />);
    expect(wrapper.find('[data-test="button-text"]').first().text()).toBe('Filter');
  });

  it('should show the first service name when there are some filters', () => {
    expect(wrapper.find('[data-test="button-text"]').first().text()).toBe('service-A');
  });
});
