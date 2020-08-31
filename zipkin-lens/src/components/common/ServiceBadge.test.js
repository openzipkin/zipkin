/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import ServiceBadge from './ServiceBadge';

describe('<ServiceBadge />', () => {
  describe('should render a label correctly', () => {
    it('only serviceName', () => {
      const wrapper = mount(<ServiceBadge serviceName="serviceA" />);
      const item = wrapper.find('[data-test="badge"]').first();
      expect(item.text()).toBe('serviceA');
    });

    it('with count', () => {
      const wrapper = mount(<ServiceBadge serviceName="serviceA" count={8} />);
      const item = wrapper.find('[data-test="badge"]').first();
      expect(item.text()).toBe('serviceA (8)');
    });
  });

  it('should render delete button when onDelete is set', () => {
    const wrapper = mount(
      <ServiceBadge
        serviceName="serviceA"
        onClick={() => {}}
        onDelete={() => {}}
      />,
    );
    const items = wrapper.find('[data-test="delete-button"]');
    expect(items.hostNodes().length).toBe(1);
  });
});
