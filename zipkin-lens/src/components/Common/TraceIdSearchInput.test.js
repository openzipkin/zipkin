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

import render from '../../test/util/render-with-default-settings';

import { TraceIdSearchInputImpl } from './TraceIdSearchInput';

describe('<TraceIdSearchInput />', () => {
  let history;
  beforeEach(() => {
    history = { push: jest.fn() };
  });

  it('should render Tooltip when hovered', async () => {
    const { findByText, getByTestId } = render(
      <TraceIdSearchInputImpl history={history} />,
    );
    fireEvent.mouseEnter(getByTestId('search-input-text'));

    const tooltipText = await findByText('Search by Trace ID');
    expect(tooltipText).toBeInTheDocument();
  });

  it('should render TextField', () => {
    const { getByTestId } = render(
      <TraceIdSearchInputImpl history={history} />,
    );
    expect(getByTestId('search-input-text')).toBeInTheDocument();
  });

  it('should call push when Enter is pushed', () => {
    const { getByTestId } = render(
      <TraceIdSearchInputImpl history={history} />,
    );

    const input = getByTestId('search-input-text');
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(history.push.mock.calls).toHaveLength(1);
  });
});
