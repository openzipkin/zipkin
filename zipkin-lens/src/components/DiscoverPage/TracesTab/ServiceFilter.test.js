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
import { fireEvent, waitForElement, within } from '@testing-library/react';
import React from 'react';

import render from '../../../test/util/render-with-default-settings';

import ServiceFilter from './ServiceFilter';

describe('<ServiceFilter />', () => {
  const props = {
    filters: ['service-A', 'service-B'],
    allServiceNames: ['service-A', 'service-B', 'service-C', 'service-D'],
    onAddFilter: jest.fn(),
    onDeleteFilter: jest.fn(),
  };

  it('should not show Popover when mounted', () => {
    const { queryByTestId } = render(<ServiceFilter {...props} />);

    expect(queryByTestId('service-filter-popover')).not.toBeInTheDocument();
  });

  it('should show Popover when the button is clicked', async () => {
    const { getByTestId } = render(<ServiceFilter {...props} />);

    const button = getByTestId('button');
    fireEvent.click(button);

    const popover = await waitForElement(() =>
      getByTestId('service-filter-popover'),
    );
    expect(popover).toBeInTheDocument();
  });

  it('should hide badge if the number of filters is 1', () => {
    const { getByTestId } = render(
      <ServiceFilter {...props} filters={['service-A']} />,
    );

    const badgeWrapper = getByTestId('badge');
    const badge = badgeWrapper.querySelector('.MuiBadge-badge');
    expect(badge).toHaveClass('MuiBadge-invisible');
  });

  it('should hide badge if the number of filters is 0', () => {
    const { getByTestId } = render(<ServiceFilter {...props} filters={[]} />);

    const badgeWrapper = getByTestId('badge');
    const badge = badgeWrapper.querySelector('.MuiBadge-badge');
    expect(badge).toHaveClass('MuiBadge-invisible');
  });

  it('should show the badge content', () => {
    const { getByTestId } = render(<ServiceFilter {...props} />);

    const badgeWrapper = getByTestId('badge');
    const badge = badgeWrapper.querySelector('.MuiBadge-badge');
    expect(badge).not.toHaveClass('MuiBadge-invisible');
    expect(within(badge).getByText('+1')).toBeInTheDocument();
  });

  it('should not show a service name when there are not any filters', () => {
    const { getByTestId } = render(<ServiceFilter {...props} filters={[]} />);
    expect(
      within(getByTestId('button-text')).getByText('Filter'),
    ).toBeInTheDocument();
  });

  it('should show the first service name when there are some filters', () => {
    const { getByTestId } = render(<ServiceFilter {...props} />);
    expect(
      within(getByTestId('button-text')).getByText('service-A'),
    ).toBeInTheDocument();
  });
});
