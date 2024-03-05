/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import React from 'react';

import CriterionBox from './CriterionBox';
import render from '../../../test/util/render-with-default-settings';

describe('<CriterionBox />', () => {
  afterEach(cleanup);
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
    onFocus: vi.fn(),
    onBlur: vi.fn(),
    onDecide: vi.fn(),
    onChange: vi.fn(),
    onDelete: vi.fn(),
    loadAutocompleteValues: vi.fn(),
  };

  it('should show an input element when focused', () => {
    render(<CriterionBox {...commonProps} />);
    const items = screen.getAllByTestId('criterion-input');
    expect(items.length).toBe(1);
  });

  it('should filter key suggestions', () => {
    render(<CriterionBox {...commonProps} />);

    expect(screen.queryAllByText('serviceName').length).toBe(1);
    // spanName and remoteServiceName are not displayed until serviceName is selected.
    expect(screen.queryAllByText('spanName').length).toBe(0);
    expect(screen.queryAllByText('remoteServiceName').length).toBe(0);
    expect(screen.queryAllByText('maxDuration').length).toBe(1);
    expect(screen.queryAllByText('minDuration').length).toBe(1);
    expect(screen.queryAllByText('tagQuery').length).toBe(1);
    expect(screen.queryAllByText('keyA').length).toBe(1);
    expect(screen.queryAllByText('keyB').length).toBe(1);
    expect(screen.queryAllByText('keyC').length).toBe(1);

    const items = screen.getAllByTestId('criterion-input');
    fireEvent.change(items[0], { target: { value: 's' } });

    expect(screen.queryAllByText('serviceName').length).toBe(1);
    // spanName and remoteServiceName are not displayed until serviceName is selected.
    expect(screen.queryAllByText('spanName').length).toBe(0);
    expect(screen.queryAllByText('remoteServiceName').length).toBe(0);
    expect(screen.queryAllByText('maxDuration').length).toBe(0);
    expect(screen.queryAllByText('minDuration').length).toBe(0);
    expect(screen.queryAllByText('tagQuery').length).toBe(0);
    expect(screen.queryAllByText('keyA').length).toBe(0);
    expect(screen.queryAllByText('keyB').length).toBe(0);
    expect(screen.queryAllByText('keyC').length).toBe(0);
  });

  it('should show spanName and remoteServiceName after serviceName is selected', () => {
    render(
      <CriterionBox
        {...commonProps}
        criteria={[
          /* serviceName was selected */
          { key: 'serviceName', value: 'serviceA' },
        ]}
      />,
    );
    expect(screen.queryAllByText('serviceName').length).toBe(0);
    // Show spanName and remoteServiceName.
    expect(screen.queryAllByText('spanName').length).toBe(1);
    expect(screen.queryAllByText('remoteServiceName').length).toBe(1);
    expect(screen.queryAllByText('maxDuration').length).toBe(1);
    expect(screen.queryAllByText('minDuration').length).toBe(1);
    expect(screen.queryAllByText('tagQuery').length).toBe(1);
    expect(screen.queryAllByText('keyA').length).toBe(1);
    expect(screen.queryAllByText('keyB').length).toBe(1);
    expect(screen.queryAllByText('keyC').length).toBe(1);
  });

  it('should filter value suggestions', () => {
    render(
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
    const items = screen.getAllByTestId('criterion-input');
    fireEvent.change(items[0], { target: { value: 'serviceName=service' } });
    expect(screen.queryAllByText('service10').length).toBe(1);
    expect(screen.queryAllByText('service11').length).toBe(1);
    expect(screen.queryAllByText('service12').length).toBe(1);
    expect(screen.queryAllByText('service20').length).toBe(1);
    expect(screen.queryAllByText('service21').length).toBe(1);
    expect(screen.queryAllByText('service22').length).toBe(1);

    fireEvent.change(items[0], { target: { value: 'serviceName=service1' } });

    expect(screen.queryAllByText('service10').length).toBe(1);
    expect(screen.queryAllByText('service11').length).toBe(1);
    expect(screen.queryAllByText('service12').length).toBe(1);
    expect(screen.queryAllByText('service20').length).toBe(0);
    expect(screen.queryAllByText('service21').length).toBe(0);
    expect(screen.queryAllByText('service22').length).toBe(0);

    fireEvent.change(items[0], { target: { value: 'serviceName=service11' } });

    expect(screen.queryAllByText('service10').length).toBe(0);
    expect(screen.queryAllByText('service11').length).toBe(1);
    expect(screen.queryAllByText('service12').length).toBe(0);
    expect(screen.queryAllByText('service20').length).toBe(0);
    expect(screen.queryAllByText('service21').length).toBe(0);
    expect(screen.queryAllByText('service22').length).toBe(0);
  });

  it("should insert '=' when Enter key is down while entering key", () => {
    render(<CriterionBox {...commonProps} />);
    const items = screen.getAllByTestId('criterion-input');
    fireEvent.change(items[0], { target: { value: 'serviceName' } });
    fireEvent.keyDown(items[0], { key: 'Enter' });
    expect(items[0].value).toBe('serviceName=');
  });

  it('should decide when Enter key is down while entering value', () => {
    const onDecide = vi.fn();
    const { getAllByTestId } = render(
      <CriterionBox {...commonProps} onDecide={onDecide} />,
    );
    const items = getAllByTestId('criterion-input');
    fireEvent.change(items[0], { target: { value: 'serviceName=serviceA' } });
    fireEvent.keyDown(items[0], { key: 'Enter' });
    expect(onDecide.call.length).toBe(1);
  });
});
