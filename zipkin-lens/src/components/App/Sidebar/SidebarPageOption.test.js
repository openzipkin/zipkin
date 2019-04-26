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
import { Link } from 'react-router-dom';

import SidebarPageOption from './SidebarPageOption';

describe('<SidebarPageOption />', () => {
  it('should have proper components and classes when it is selected', () => {
    const wrapper = shallow(
      <SidebarPageOption
        location={{ pathname: '/zipkin' }}
        pageName="browser"
      />,
    );
    expect(wrapper.find('.sidebar__page-option').length).toEqual(1);
    expect(wrapper.find('.sidebar__page-option--selected').length).toEqual(1); // selected
  });

  it('should have proper components and classes when it is not selected', () => {
    const wrapper = shallow(
      <SidebarPageOption
        location={{ pathname: '/zipkin' }}
        pageName="dependencies"
      />,
    );
    expect(wrapper.find('.sidebar__page-option').length).toEqual(1);
    expect(wrapper.find('.sidebar__page-option--selected').length).toEqual(0); // not selected
  });

  it('should have proper path and label in Browser', () => {
    const wrapper = shallow(
      <SidebarPageOption
        location={{ pathname: '/zipkin' }}
        pageName="browser"
      />,
    );
    expect(wrapper.find(Link).filterWhere(
      item => item.prop('to').pathname === '/zipkin',
    ).length).toEqual(1);
    expect(wrapper.find('.sidebar__page-option-label').text()).toEqual('Search');
  });

  it('should have proper path and label in Dependencies', () => {
    const wrapper = shallow(
      <SidebarPageOption
        location={{ pathname: '/zipkin' }}
        pageName="dependencies"
      />,
    );
    expect(wrapper.find(Link).filterWhere(
      item => item.prop('to').pathname === '/zipkin/dependency',
    ).length).toEqual(1);
    expect(wrapper.find('.sidebar__page-option-label').text()).toEqual('Dependencies');
  });
});
