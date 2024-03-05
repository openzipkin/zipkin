/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { createMemoryHistory } from 'history';
import React from 'react';

import NodeDetailData from './NodeDetailData';
import render from '../../test/util/render-with-default-settings';

describe('<NodeDetailData />', () => {
  it('should go to the search traces page when the search traces button is clicked.', () => {
    const history = createMemoryHistory();

    render(
      <NodeDetailData
        serviceName="serviceA"
        targetEdges={[]}
        sourceEdges={[]}
      />,
      { history },
    );

    fireEvent.click(screen.getByTestId('search-traces-button'));

    expect(history.location.pathname).toBe('/');
    expect(history.location.search).toBe('?serviceName=serviceA');
  });
});
