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
import { faHome } from '@fortawesome/free-solid-svg-icons';
import React from 'react';

import render from '../../test/util/render-with-default-settings';

import SidebarMenu from './SidebarMenu';

describe('<SidebarMenu />', () => {
  it('renders relative link to be non-external', () => {
    const { getByTestId } = render(
      <SidebarMenu
        title="tooltip"
        path="/traces"
        icon={faHome}
        data-testid="sidebar-menu-link"
      />,
    );
    const link = getByTestId('sidebar-menu-link');
    expect(link).toHaveAttribute('href', '/traces');
    expect(link).not.toHaveAttribute('target');
  });

  it('renders absolute link to be external', () => {
    const { getByTestId } = render(
      <SidebarMenu
        title="tooltip"
        path="https://github.com/openzipkin"
        icon={faHome}
        data-testid="sidebar-menu-link"
      />,
    );
    const link = getByTestId('sidebar-menu-link');
    expect(link).toHaveAttribute('href', 'https://github.com/openzipkin');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'no-opener');
  });
});
