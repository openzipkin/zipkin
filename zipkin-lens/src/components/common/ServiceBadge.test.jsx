/*
 * Copyright 2015-2023 The OpenZipkin Authors
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
import { describe, it, expect, afterEach } from 'vitest';
import React from 'react';
import { render, cleanup } from '@testing-library/react';
import ServiceBadge from './ServiceBadge';
import { render, screen } from '@testing-library/react';

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
    const { getByTestId } = render(
      <ServiceBadge
        serviceName="serviceA"
        onClick={() => {}}
        onDelete={() => {}}
      />,
    );
    const items = getByTestId('delete-button');
    expect(items.children.length).toBe(1);
  });
});
