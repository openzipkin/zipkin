/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { fireEvent, screen } from '@testing-library/react';
import React from 'react';
import { vi, describe, it, expect } from 'vitest';
import { SearchBarImpl } from './SearchBar';
import render from '../../../test/util/render-with-default-settings';

vi.mock('./CriterionBox', () => ({
  default: () => {
    const shortid2 = import('shortid');

    return ({ criterionIndex, onChange }) => (
      <input
        key="default"
        onChange={(event) => {
          const ss = event.target.value.split('=', 2);
          onChange(criterionIndex, {
            key: ss[0],
            value: ss[1],
            id: shortid2.generate(),
          });
        }}
        data-testid="criterion-box"
      />
    );
  },
}));

describe('<SearchBar />', () => {
  const commonProps = {
    criteria: [],
    onChange: vi.fn(),
    serviceNames: [],
    isLoadingServiceNames: false,
    spanNames: [],
    isLoadingSpanNames: false,
    remoteServiceNames: [],
    isLoadingRemoteServiceNames: false,
    autocompleteKeys: [],
    autocompleteValues: [],
    isLoadingAutocompleteValues: false,
    loadRemoteServices: vi.fn(),
    loadSpans: vi.fn(),
  };

  it('should add an empty criterion when add button is clicked', () => {
    render(<SearchBarImpl {...commonProps} />);
    fireEvent.click(screen.getByTestId('add-button'));
    expect(commonProps.onChange.mock.calls.length).toBe(1);
    expect(commonProps.onChange.mock.calls[0][0][0].key).toEqual('');
    expect(commonProps.onChange.mock.calls[0][0][0].value).toEqual('');
  });

  /*
  it('should load spans and remote services when service name is changed', () => {
    let criteria = [
      { key: 'serviceName', value: 'serviceA', id: shortid.generate() },
    ];
    const onChange = (newCriteria) => {
      criteria = newCriteria;
    };

    let props = { ...commonProps, criteria, onChange };
    const { rerender } = render(<SearchBarImpl {...props} />);

    expect(commonProps.loadSpans.mock.calls.length).toBe(1);
    expect(commonProps.loadRemoteServices.mock.calls.length).toBe(1);
    expect(commonProps.loadSpans.mock.calls[0][0]).toBe('serviceA');
    expect(commonProps.loadRemoteServices.mock.calls[0][0]).toBe('serviceA');

    // serviceA -> serviceB
    fireEvent.change(screen.getByTestId('criterion-box'), {
      target: { value: 'serviceName=serviceB' },
    });

    props = { ...commonProps, criteria, onChange };
    rerender(<SearchBarImpl {...props} />);

    expect(commonProps.loadSpans.mock.calls.length).toBe(2);
    expect(commonProps.loadRemoteServices.mock.calls.length).toBe(2);
    expect(commonProps.loadSpans.mock.calls[1][0]).toBe('serviceB');
    expect(commonProps.loadRemoteServices.mock.calls[1][0]).toBe('serviceB');

    // serviceB -> serviceB
    // If there is no change, nothing will be done.
    fireEvent.change(screen.getByTestId('criterion-box'), {
      target: { value: 'serviceName=serviceB' },
    });
    expect(commonProps.loadSpans.mock.calls.length).toBe(2);
    expect(commonProps.loadRemoteServices.mock.calls.length).toBe(2);
  }); */
});
