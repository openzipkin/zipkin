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

import ServiceFilterPopover from './ServiceFilterPopover';

describe('<ServiceFilterPopover />', () => {
  let wrapper;

  const props = {
    open: true,
    anchorEl: document.createElement('div'),
    onClose: jest.fn(),
    filters: ['service-A', 'serviceD'],
    allServiceNames: ['service-A', 'service-B', 'service-C', 'serviceD'],
    onAddFilter: jest.fn(),
    onDeleteFilter: jest.fn(),
  };

  beforeEach(() => {
    wrapper = mount(
      <ServiceFilterPopover {...props} />,
    );
  });

  it('should show a title label', () => {
    const item = wrapper.find('[data-test="label"]').first();
    expect(item.text()).toBe('Filter');
  });

  it('should change text value when the TextField is changed', () => {
    const item = wrapper.find('input').first();
    item.simulate('change', { target: { value: 'service-A' } });
    expect(wrapper.find('[data-test="text-field"]').first().prop('value')).toBe('service-A');
  });

  it('should now show filter list when there are not any filters', () => {
    wrapper = mount(<ServiceFilterPopover {...props} filters={[]} />);
    const items = wrapper.find('[data-test="filters"]');
    expect(items.length).toBe(0);
  });
});
