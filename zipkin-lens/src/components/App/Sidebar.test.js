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
import Drawer from '@material-ui/core/Drawer';
import { createShallow } from '@material-ui/core/test-utils';

import Sidebar from './Sidebar';
import Logo from '../../img/zipkin-logo.svg';
import SidebarMenuItem from './SidebarMenuItem';

describe('<Sidebar />', () => {
  let shallow;

  beforeEach(() => {
    shallow = createShallow();
  });

  it('should render Drawer', () => {
    const wrapper = shallow(<Sidebar />);
    expect(wrapper.find(Drawer).length).toBe(1);
  });

  it('should render Logo', () => {
    const wrapper = shallow(<Sidebar />);
    expect(wrapper.find(Logo).length).toBe(1);
  });

  it('should render internal links', () => {
    const wrapper = shallow(<Sidebar />);
    const list = wrapper.find('[data-testid="internal-links"]');
    // Only discover page and dependencies page are the internal link (links routed in JavaScript).
    expect(list.find(SidebarMenuItem).length).toBe(2);
  });

  it('should render external links', () => {
    const wrapper = shallow(<Sidebar />);
    const list = wrapper.find('[data-testid="external-links"]');
    // External links are zipkin home page, github repository, twitter, and gitter.
    expect(list.find(SidebarMenuItem).length).toBe(4);
  });
});
