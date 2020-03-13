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

import ServiceFilterPopover from './ServiceFilterPopover';

describe('<ServiceFilterPopover />', () => {
  const props = {
    open: true,
    anchorEl: document.createElement('div'),
    onClose: jest.fn(),
    filters: ['service-A', 'serviceD'],
    allServiceNames: ['service-A', 'service-B', 'service-C', 'serviceD'],
    onAddFilter: jest.fn(),
    onDeleteFilter: jest.fn(),
  };

  it('should show a title label', async () => {
    const { getByTestId } = render(<ServiceFilterPopover {...props} />);
    const item = getByTestId('label');
    expect(item).toBeInTheDocument();
    expect(within(item).getByText('Filter')).toBeInTheDocument();
  });

  it('should change text value when the TextField is changed', async () => {
    const { getByTestId } = render(<ServiceFilterPopover {...props} />);
    const input = within(getByTestId('text-field')).getByRole('textbox');

    fireEvent.change(input, { target: { value: 'service-A' } });
    const updatedInput = await waitForElement(() =>
      within(getByTestId('text-field')).getByRole('textbox'),
    );

    expect(updatedInput.value).toEqual('service-A');
  });

  it('should not show filter list when there are not any filters', () => {
    const { queryByTestId } = render(
      <ServiceFilterPopover {...props} filters={[]} />,
    );
    expect(queryByTestId('filters')).not.toBeInTheDocument();
  });
});
