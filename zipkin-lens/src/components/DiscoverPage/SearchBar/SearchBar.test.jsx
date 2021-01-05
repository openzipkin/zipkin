/*
 * Copyright 2015-2021 The OpenZipkin Authors
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
import shortid from 'shortid';

import { SearchBarImpl } from './SearchBar';
import render from '../../../test/util/render-with-default-settings';

jest.mock('./CriterionBox', () => {
  // eslint-disable-next-line global-require,no-shadow
  const shortid = require('shortid');

  // eslint-disable-next-line react/prop-types
  return ({ criterionIndex, onChange }) => (
    <input
      onChange={(event) => {
        const ss = event.target.value.split('=', 2);
        onChange(criterionIndex, {
          key: ss[0],
          value: ss[1],
          id: shortid.generate(),
        });
      }}
      data-testid="criterion-box"
    />
  );
});

describe('<SearchBar />', () => {
  afterEach(() => jest.restoreAllMocks());

  const commonProps = {
    criteria: [],
    onChange: jest.fn(),
    serviceNames: [],
    isLoadingServiceNames: false,
    spanNames: [],
    isLoadingSpanNames: false,
    remoteServiceNames: [],
    isLoadingRemoteServiceNames: false,
    autocompleteKeys: [],
    autocompleteValues: [],
    isLoadingAutocompleteValues: false,
    loadRemoteServices: jest.fn(),
    loadSpans: jest.fn(),
  };

  it('should add an empty criterion when add button is clicked', () => {
    const { getByTestId } = render(<SearchBarImpl {...commonProps} />);
    fireEvent.click(getByTestId('add-button'));
    expect(commonProps.onChange.mock.calls.length).toBe(1);
    expect(commonProps.onChange.mock.calls[0][0][0].key).toEqual('');
    expect(commonProps.onChange.mock.calls[0][0][0].value).toEqual('');
  });

  it('should load spans and remote services when service name is changed', () => {
    let criteria = [
      { key: 'serviceName', value: 'serviceA', id: shortid.generate() },
    ];
    const onChange = (newCriteria) => {
      criteria = newCriteria;
    };

    let props = { ...commonProps, criteria, onChange };
    const { getByTestId, rerender } = render(<SearchBarImpl {...props} />);

    expect(commonProps.loadSpans.mock.calls.length).toBe(1);
    expect(commonProps.loadRemoteServices.mock.calls.length).toBe(1);
    expect(commonProps.loadSpans.mock.calls[0][0]).toBe('serviceA');
    expect(commonProps.loadRemoteServices.mock.calls[0][0]).toBe('serviceA');

    // serviceA -> serviceB
    fireEvent.change(getByTestId('criterion-box'), {
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
    fireEvent.change(getByTestId('criterion-box'), {
      target: { value: 'serviceName=serviceB' },
    });
    expect(commonProps.loadSpans.mock.calls.length).toBe(2);
    expect(commonProps.loadRemoteServices.mock.calls.length).toBe(2);
  });
});
