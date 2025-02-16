/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { render, screen } from '@testing-library/react';
import { afterEach, it, describe, expect, vi } from 'vitest';
import React, { Suspense } from 'react';

import { UI_CONFIG } from '../../constants/api';
import { defaultConfig } from './constants';

afterEach(() => {
  vi.restoreAllMocks();
  vi.resetModules();
});

const renderUiConfig = async () => {
  const { UiConfigConsumer, UiConfig } = await import('./UiConfig');
  render(
    <Suspense fallback="Suspended">
      <UiConfig>
        <UiConfigConsumer>
          {(value) => <div>{JSON.stringify(value)}</div>}
        </UiConfigConsumer>
      </UiConfig>
    </Suspense>,
  );
};

describe('<UiConfig />', () => {
  it('fetches config, suspends until response, renders after response', async () => {
    // Prepare the expected config object
    const config = { ...defaultConfig, defaultLookback: 100 };
    const configJson = JSON.stringify(config);

    // Create a promise that can be resolved later
    let resolve;
    const configPromise = new Promise((r) => (resolve = r));
    const fetchSpy = vi
      .spyOn(global, 'fetch')
      .mockImplementationOnce(() => configPromise);

    // Render component and check suspension state
    await renderUiConfig();
    expect(screen.getByText('Suspended')).not.toBeNull();
    expect(fetchSpy).toHaveBeenCalledWith(UI_CONFIG);

    resolve(new Response(configJson));
    // We need to get off the processing loop to allow the promise to complete and resolve the
    // config.
    await new Promise((res) => setTimeout(res, 10));

    // Check that the expected JSON is rendered
    expect(screen.getByText(configJson)).not.toBeNull();
  });
});
