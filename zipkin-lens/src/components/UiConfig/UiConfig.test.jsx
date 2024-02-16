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
    const config = { defaultLookback: 100 };
    Object.keys(defaultConfig).forEach((key) => {
      config[key] = config[key] || defaultConfig[key];
    });
    const configJson = JSON.stringify(config);

    let resolve;
    const configPromise = new Promise((r) => {
      resolve = r;
      return undefined;
    });
    const fetchSpy = vi
      .spyOn(global, 'fetch')
      .mockImplementationOnce(() => configPromise);

    await renderUiConfig();
    expect(screen.getAllByText('Suspended')).length(1);

    expect(fetchSpy).toHaveBeenCalledWith(UI_CONFIG);

    resolve(new Response(configJson));
    // We need to get off the processing loop to allow the promise to complete and resolve the
    // config.
    await new Promise((resolve) => setTimeout(resolve, 1));

    expect(screen.getByText(JSON.stringify(config))).toBeDefined();
  });
});
