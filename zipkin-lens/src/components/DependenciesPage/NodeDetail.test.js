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
import { render, cleanup } from '@testing-library/react';
import { ThemeProvider } from '@material-ui/styles';

import NodeDetail from './NodeDetail';
import { theme } from '../../colors';

describe('<NodeDetail />', () => {
  const commonProps = {
    serviceName: 'Example Service Name',
    targetEdges: [
      { target: 'serviceD', metrics: { normal: 0, danger: 0 } },
      { target: 'serviceE', metrics: { normal: 0, danger: 0 } },
      { target: 'serviceF', metrics: { normal: 0, danger: 0 } },
    ],
    sourceEdges: [
      { source: 'serviceA', metrics: { normal: 0, danger: 0 } },
      { source: 'serviceB', metrics: { normal: 0, danger: 0 } },
    ],
    minHeight: 100,
    classes: {},
  };

  afterEach(() => {
    cleanup();
  });

  it('should render "Not found" message when targetEdges is empty', () => {
    const { queryAllByText } = render(
      <ThemeProvider theme={theme}>
        <NodeDetail {...commonProps} targetEdges={[]} />
      </ThemeProvider>,
    );
    const notFoundMessages = queryAllByText('Not found...');
    expect(notFoundMessages.length).toBe(1);
  });

  it('should render "Not found" message when sourceEdges is empty', () => {
    const { queryAllByText } = render(
      <ThemeProvider theme={theme}>
        <NodeDetail {...commonProps} sourceEdges={[]} />
      </ThemeProvider>,
    );
    const notFoundMessages = queryAllByText('Not found...');
    expect(notFoundMessages.length).toBe(1);
  });

  it('should not render "Not found" message when targetEdges and sourceEdges are not empty', () => {
    const { queryAllByText } = render(
      <ThemeProvider theme={theme}>
        <NodeDetail {...commonProps} />
      </ThemeProvider>,
    );
    const notFoundMessages = queryAllByText('Not found...');
    expect(notFoundMessages.length).toBe(0);
  });
});
