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
import { shallow } from 'enzyme';
import React from 'react';
import Box from '@material-ui/core/Box';
import ListItem from '@material-ui/core/ListItem';
import Tooltip from '@material-ui/core/Tooltip';

import SidebarMenuItem from './SidebarMenuItem';
import { theme } from '../../colors';

describe('<SidebarMenuItem />', () => {
  describe('should render components', () => {
    let wrapper;

    beforeEach(() => {
      wrapper = shallow(
        <SidebarMenuItem.WrappedComponent
          history={{ push: () => {} }}
          location={{ pathname: '/zipkin' }}
          isExternalLink
          title="Title"
          url="http://example.com"
          buttonClassName="fas fa-search"
        />,
      );
    });

    it('should render Tooltip', () => {
      const items = wrapper.find(Tooltip);
      expect(items.length).toBe(1);
      expect(items.at(0).props().title).toBe('Title');
    });

    it('should render ListItem', () => {
      const items = wrapper.find(ListItem);
      expect(items.length).toBe(1);
    });

    it('should render Box', () => {
      const items = wrapper.find(Box);
      expect(items.length).toBe(1);
      expect(items.at(0).props().className).toBe('fas fa-search');
    });
  });

  describe('should render internal links', () => {
    let pushMock;
    let commonProps;

    beforeEach(() => {
      pushMock = jest.fn();

      commonProps = {
        history: { push: pushMock },
        title: 'Some Page',
        url: '/zipkin/somePage',
        buttonClassName: 'fas fa-search',
      };
    });

    it('should be lighter when the current location equals to the url', () => {
      const wrapper = shallow(
        <SidebarMenuItem.WrappedComponent
          {...commonProps}
          location={{ pathname: '/zipkin/somePage' }}
        />,
      );
      expect(wrapper.find(ListItem).props().style).toEqual({
        color: theme.palette.common.white,
        backgroundColor: theme.palette.primary.dark,
      });
    });

    it('should be darker when the current location does not equal to the url', () => {
      const wrapper = shallow(
        <SidebarMenuItem.WrappedComponent
          {...commonProps}
          location={{ pathname: '/zipkin/otherPage' }}
        />,
      );
      expect(wrapper.find(ListItem).props().style).toBeNull();
    });

    it('should push when clicked', () => {
      const wrapper = shallow(
        <SidebarMenuItem.WrappedComponent
          {...commonProps}
          location={{ pathname: '/zipkin/somePage' }}
        />,
      );
      wrapper.find(ListItem).simulate('click');
      expect(pushMock.mock.calls.length).toBe(1);
    });
  });

  describe('should render external links', () => {
    let pushMock;
    let commonProps;

    beforeEach(() => {
      pushMock = jest.fn();

      commonProps = {
        history: { push: pushMock },
        location: { pathname: '/zipkin/somePage' },
        isExternalLink: true,
        title: 'Some Page',
        url: 'https://example.com',
        buttonClassName: 'fas fa-search',
      };
    });

    it('should pass some props', () => {
      const wrapper = shallow(<SidebarMenuItem.WrappedComponent {...commonProps} />);
      const items = wrapper.find(ListItem);
      expect(items.props().component).toBe('a');
      expect(items.props().href).toBe('https://example.com');
    });

    it('should not call push when clicked', () => {
      const wrapper = shallow(<SidebarMenuItem.WrappedComponent {...commonProps} />);
      wrapper.find(ListItem).simulate('click');
      expect(pushMock.mock.calls.length).toBe(0);
    });
  });
});
