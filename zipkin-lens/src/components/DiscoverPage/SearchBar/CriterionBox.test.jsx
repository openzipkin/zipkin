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

import { fireEvent } from '@testing-library/react';
import React from 'react';

import CriterionBox from './CriterionBox';
import render from '../../../test/util/render-with-default-settings';

describe('<CriterionBox />', () => {
  const commonProps = {
    criteria: [],
    criterion: { key: '', value: '' },
    serviceNames: ['serviceA', 'serviceB', 'serviceC'],
    spanNames: ['spanA', 'spanB', 'spanC'],
    autocompleteKeys: ['keyA', 'keyB', 'keyC'],
    autocompleteValues: ['valueA', 'valueB', 'valueC'],
    isLoadingServiceNames: false,
    isLoadingRemoteServiceNames: false,
    isLoadingSpanNames: false,
    isLoadingAutocompleteValues: false,
    isFocused: true,
    onFocus: jest.fn(),
    onBlur: jest.fn(),
    onDecide: jest.fn(),
    onChange: jest.fn(),
    onDelete: jest.fn(),
    loadAutocompleteValues: jest.fn(),
  };

  it('should show an input element when focused', () => {
    const { getAllByTestId } = render(<CriterionBox {...commonProps} />);
    const items = getAllByTestId('criterion-input');
    expect(items.length).toBe(1);
  });

  it('should filter key suggestions', () => {
    const { getAllByTestId, queryAllByText } = render(
      <CriterionBox {...commonProps} />,
    );

    expect(queryAllByText('serviceName').length).toBe(1);
    // spanName and remoteServiceName are not displayed until serviceName is selected.
    expect(queryAllByText('spanName').length).toBe(0);
    expect(queryAllByText('remoteServiceName').length).toBe(0);
    expect(queryAllByText('maxDuration').length).toBe(1);
    expect(queryAllByText('minDuration').length).toBe(1);
    expect(queryAllByText('tagQuery').length).toBe(1);
    expect(queryAllByText('keyA').length).toBe(1);
    expect(queryAllByText('keyB').length).toBe(1);
    expect(queryAllByText('keyC').length).toBe(1);

    const items = getAllByTestId('criterion-input');
    fireEvent.change(items[0], { target: { value: 's' } });

    expect(queryAllByText('serviceName').length).toBe(1);
    // spanName and remoteServiceName are not displayed until serviceName is selected.
    expect(queryAllByText('spanName').length).toBe(0);
    expect(queryAllByText('remoteServiceName').length).toBe(0);
    expect(queryAllByText('maxDuration').length).toBe(0);
    expect(queryAllByText('minDuration').length).toBe(0);
    expect(queryAllByText('tagQuery').length).toBe(0);
    expect(queryAllByText('keyA').length).toBe(0);
    expect(queryAllByText('keyB').length).toBe(0);
    expect(queryAllByText('keyC').length).toBe(0);
  });

  it('should show spanName and remoteServiceName after serviceName is selected', () => {
    const { queryAllByText } = render(
      <CriterionBox
        {...commonProps}
        criteria={[
          /* serviceName was selected */
          { key: 'serviceName', value: 'serviceA' },
        ]}
      />,
    );
    expect(queryAllByText('serviceName').length).toBe(0);
    // Show spanName and remoteServiceName.
    expect(queryAllByText('spanName').length).toBe(1);
    expect(queryAllByText('remoteServiceName').length).toBe(1);
    expect(queryAllByText('maxDuration').length).toBe(1);
    expect(queryAllByText('minDuration').length).toBe(1);
    expect(queryAllByText('tagQuery').length).toBe(1);
    expect(queryAllByText('keyA').length).toBe(1);
    expect(queryAllByText('keyB').length).toBe(1);
    expect(queryAllByText('keyC').length).toBe(1);
  });

  it('should filter value suggestions', () => {
    const { getAllByTestId, queryAllByText } = render(
      <CriterionBox
        {...commonProps}
        serviceNames={[
          'service10',
          'service11',
          'service12',
          'service20',
          'service21',
          'service22',
        ]}
      />,
    );
    const items = getAllByTestId('criterion-input');
    fireEvent.change(items[0], { target: { value: 'serviceName=service' } });
    expect(queryAllByText('service10').length).toBe(1);
    expect(queryAllByText('service11').length).toBe(1);
    expect(queryAllByText('service12').length).toBe(1);
    expect(queryAllByText('service20').length).toBe(1);
    expect(queryAllByText('service21').length).toBe(1);
    expect(queryAllByText('service22').length).toBe(1);

    fireEvent.change(items[0], { target: { value: 'serviceName=service1' } });

    expect(queryAllByText('service10').length).toBe(1);
    expect(queryAllByText('service11').length).toBe(1);
    expect(queryAllByText('service12').length).toBe(1);
    expect(queryAllByText('service20').length).toBe(0);
    expect(queryAllByText('service21').length).toBe(0);
    expect(queryAllByText('service22').length).toBe(0);

    fireEvent.change(items[0], { target: { value: 'serviceName=service11' } });

    expect(queryAllByText('service10').length).toBe(0);
    expect(queryAllByText('service11').length).toBe(1);
    expect(queryAllByText('service12').length).toBe(0);
    expect(queryAllByText('service20').length).toBe(0);
    expect(queryAllByText('service21').length).toBe(0);
    expect(queryAllByText('service22').length).toBe(0);
  });

  it("should insert '=' when Enter key is down while entering key", () => {
    const { getAllByTestId } = render(<CriterionBox {...commonProps} />);
    const items = getAllByTestId('criterion-input');
    fireEvent.change(items[0], { target: { value: 'serviceName' } });
    fireEvent.keyDown(items[0], { key: 'Enter' });
    expect(items[0].value).toBe('serviceName=');
  });

  it('should decide when Enter key is down while entering value', () => {
    const onDecide = jest.fn();
    const { getAllByTestId } = render(
      <CriterionBox {...commonProps} onDecide={onDecide} />,
    );
    const items = getAllByTestId('criterion-input');
    fireEvent.change(items[0], { target: { value: 'serviceName=serviceA' } });
    fireEvent.keyDown(items[0], { key: 'Enter' });
    expect(onDecide.call.length).toBe(1);
  });
});
