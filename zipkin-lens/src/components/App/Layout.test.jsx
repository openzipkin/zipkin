/*
 * Copyright 2015-2024 The OpenZipkin Authors
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

import { describe, it, expect, afterEach } from 'vitest';
import { cleanup, screen } from '@testing-library/react';
import render from '../../test/util/render-with-default-settings';
import Layout from './Layout';

describe('<Layout />', () => {
  afterEach(cleanup);
  it('does not render help link with default config', () => {
    // children is required so avoid warning by passing dummy children
    render(
      <Layout>
        <span>Test</span>
        <span>Test</span>
      </Layout>,
    );
    const helpLink = screen.queryByTitle('Support');
    expect(helpLink).toBeNull();
  });

  it('does render help link when defined', () => {
    // children is required so avoid warning by passing dummy children
    render(
      <Layout>
        <span>Test</span>
        <span>Test</span>
      </Layout>,
      {
        uiConfig: {
          supportUrl: 'https://gitter.im/openzipkin/zipkin',
        },
      },
    );
    const helpLink = screen.getByTitle('Support');
    expect(helpLink).toBeDefined();
    expect(helpLink.href).toEqual('https://gitter.im/openzipkin/zipkin');
  });

  it('does render Dependencies Page with default config', () => {
    // children is required so avoid warning by passing dummy children
    render(
      <Layout>
        <span>Test</span>
        <span>Test</span>
      </Layout>,
    );
    expect(screen.queryByText('Dependencies')).toBeDefined();
  });

  it('does not render Dependencies Page when disabled', () => {
    // children is required so avoid warning by passing dummy children
    render(
      <Layout>
        <span>Test</span>
        <span>Test</span>
      </Layout>,
      {
        uiConfig: {
          dependency: {
            enabled: false,
          },
        },
      },
    );
    expect(screen.queryByTitle('Dependencies')).toBeNull();
  });
});
