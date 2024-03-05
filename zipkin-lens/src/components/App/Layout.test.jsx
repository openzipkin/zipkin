/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
