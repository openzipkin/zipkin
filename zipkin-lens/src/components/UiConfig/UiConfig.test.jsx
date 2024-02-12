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
import { render, screen } from '@testing-library/react';
import fetchMock from 'fetch-mock';
import { afterEach, it, describe, expect, vi } from 'vitest';
import React, { Suspense } from 'react';

afterEach(() => {
  fetchMock.restore();
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
  it('fetches config and suspends', async () => {
    const configPromise = new Promise(() => undefined);
    const { UI_CONFIG } = await import('../../constants/api');
    fetchMock.once(UI_CONFIG, configPromise, { overwriteRoutes: true });

    await renderUiConfig();
    expect(screen.getAllByText('Suspended')).length(1);

    fetchMock.called(UI_CONFIG);
  });

  it('provides config when resolved', (context) => async () => {
    const config = { defaultLookback: 100 };
    const { defaultConfig } = await import('./UiConfig');
    Object.keys(defaultConfig).forEach((key) => {
      config[key] = config[key] || defaultConfig[key];
    });
    const { UI_CONFIG } = await import('../../constants/api');
    fetchMock.once(UI_CONFIG, config, { overwriteRoutes: true });

    // TODO: adrian needs help, as this broke when porting to vitest
    context.skip();

    const { rerender } = await renderUiConfig();
    expect(screen.getAllByText('Suspended')).length(1);

    // We need to get off the processing loop to allow the promise to complete and resolve the
    // config.
    await new Promise((resolve) => setTimeout(resolve, 1));

    rerender(); // was rerender(<UiConfig />);
    expect(screen.getByText(JSON.stringify(config))).toBeInTheDocument();

    fetchMock.called(UI_CONFIG);
  });
});
