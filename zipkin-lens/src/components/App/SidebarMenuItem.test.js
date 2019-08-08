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
import { render, fireEvent } from '@testing-library/react';
import { createShallow } from '@material-ui/core/test-utils';

import { SidebarMenuItemImpl } from './SidebarMenuItem';

describe('<SidebarMenuItem />', () => {
  let shallow;

  beforeEach(() => {
    shallow = createShallow();
  });

  describe('should render an external link item', () => {
    const props = {
      history: { push: jest.fn() },
      location: { pathname: '/zipkin' },
      isExternal: true,
      title: 'External Link',
      links: ['http://example.com'],
      logo: 'fab fa-home',
      classes: {},
    };

    it('should render Tooltip', () => {
      const wrapper = shallow(<SidebarMenuItemImpl {...props} />);
      const tooltip = wrapper.find('[data-testid="tooltip"]');
      expect(tooltip.length).toBe(1);
      expect(tooltip.props().title).toBe('External Link');
    });

    it('should render ListItem', () => {
      const wrapper = shallow(<SidebarMenuItemImpl {...props} />);
      const listItem = wrapper.find('[data-testid="list-item"]');
      expect(listItem.length).toBe(1);
      expect(listItem.props().button).toBe(true);
      expect(listItem.props().component).toBe('a');
      expect(listItem.props().href).toBe('http://example.com');
    });

    it('should render Logo', () => {
      const wrapper = shallow(<SidebarMenuItemImpl {...props} />);
      const logo = wrapper.find('[data-testid="logo"]');
      expect(logo.length).toBe(1);
      expect(logo.props().component).toBe('span');
      expect(logo.props().className).toBe('fab fa-home');
    });
  });

  describe('should render an internal link item', () => {
    let props;

    beforeEach(() => {
      props = {
        history: { push: jest.fn() },
        location: { pathname: '/zipkin/dependency' },
        isExternal: false,
        title: 'Internal Link',
        links: ['/zipkin', '/zipkin/dependency'],
        logo: 'fab fa-home',
        classes: {},
      };
    });

    it('should render Tooltip', () => {
      const wrapper = shallow(<SidebarMenuItemImpl {...props} />);
      const tooltip = wrapper.find('[data-testid="tooltip"]');
      expect(tooltip.length).toBe(1);
      expect(tooltip.props().title).toBe('Internal Link');
    });

    it('should render ListItem', () => {
      const wrapper = shallow(<SidebarMenuItemImpl {...props} />);
      const listItem = wrapper.find('[data-testid="list-item"]');
      expect(listItem.length).toBe(1);
      expect(listItem.props().button).toBe(true);
      expect(listItem.props().onClick).toBeTruthy();
    });

    it('should render Logo', () => {
      const wrapper = shallow(<SidebarMenuItemImpl {...props} />);
      const logo = wrapper.find('[data-testid="logo"]');
      expect(logo.length).toBe(1);
      expect(logo.props().component).toBe('span');
      expect(logo.props().className).toBe('fab fa-home');
    });

    it('should call history.push when ListItem is clicked', () => {
      const { getByTestId } = render(<SidebarMenuItemImpl {...props} />);
      fireEvent.click(getByTestId('list-item'));
      expect(props.history.push.mock.calls.length).toBe(1);
      expect(props.history.push.mock.calls[0][0]).toBe('/zipkin');
    });
  });
});
