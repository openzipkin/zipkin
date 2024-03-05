/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect, afterEach } from 'vitest';
import React from 'react';
import { render, cleanup, screen } from '@testing-library/react';
import ServiceBadge from './ServiceBadge';

describe('<ServiceBadge />', () => {
  afterEach(cleanup);
  describe('should render a label correctly', () => {
    it('only serviceName', () => {
      const { getByTestId } = render(<ServiceBadge serviceName="serviceA" />);
      const item = getByTestId('badge');
      expect(item.textContent).toBe('serviceA');
    });

    it('with count', () => {
      const { getByTestId } = render(
        <ServiceBadge serviceName="serviceA" count={8} />,
      );
      const item = getByTestId('badge');
      expect(item.textContent).toBe('serviceA (8)');
    });
  });

  it('should render delete button when onDelete is set', () => {
    // eslint-disable-next-line no-empty-function
    render(<ServiceBadge serviceName="serviceA" onDelete={() => undefined} />);
    const items = screen.getByTestId('delete-button');
    expect(items.children.length).toBe(1);
  });
});
