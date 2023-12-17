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
import React from 'react';

import ServiceBadge from './ServiceBadge';
import { render, screen } from '@testing-library/react';

describe('<ServiceBadge />', () => {
  describe('should render a label correctly', () => {
    it('only serviceName', () => {
      render(<ServiceBadge serviceName="serviceA" />);
      const item = screen.getByTestId('badge');
      expect(item).toHaveTextContent('serviceA');
    });

    it('with count', () => {
      render(<ServiceBadge serviceName="serviceA" count={8} />);
      const item = screen.getByTestId('badge');
      expect(item).toHaveTextContent('serviceA (8)');
    });
  });

  it('should render delete button when onDelete is set', () => {
    render(
      <ServiceBadge
        serviceName="serviceA"
        onClick={() => {}}
        onDelete={() => {}}
      />,
    );
    expect(screen.getByTestId('delete-button')).toBeInTheDocument();
  });
});
