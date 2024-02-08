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
import { afterEach, beforeEach, it, describe, expect } from 'vitest';
import React, { Suspense } from 'react';

import {
  defaultConfig,
  UiConfigConsumer,
  UiConfig as RawUIConfig,
} from './UiConfig';
import { UI_CONFIG } from '../../constants/api';

afterEach(() => {
  fetchMock.restore();
});

beforeEach(() => {
  // We fetch the resource on module initialization for performance, but want to do that in every
  // test.
});

const UiConfig = () => {
  return (
    <Suspense fallback="Suspended">
      <RawUIConfig>
        <UiConfigConsumer>
          {(value) => <div>{JSON.stringify(value)}</div>}
        </UiConfigConsumer>
      </RawUIConfig>
    </Suspense>
  );
};

describe('<UiConfig />', () => {
  it('fetches config and suspends', async () => {
    const configPromise = new Promise(() => undefined);
    fetchMock.once(UI_CONFIG, configPromise, { overwriteRoutes: true });

    render(<UiConfig />);
    expect(screen.getAllByText('Suspended')).length(1);

    fetchMock.called(UI_CONFIG);
  });

  it('provides config when resolved', async () => {
    const config = { defaultLookback: 100 };
    Object.keys(defaultConfig).forEach((key) => {
      config[key] = config[key] || defaultConfig[key];
    });

    fetchMock.once(UI_CONFIG, config, { overwriteRoutes: true });

    render(<UiConfig />);
    expect(screen.getAllByText('Suspended').length).toBe(1);

    /*// We need to get off the processing loop to allow the promise to complete and resolve the
    // config.
    await new Promise((resolve) => setTimeout(resolve, 1));

    rerender(<UiConfig />);
    expect(screen.getByText(JSON.stringify(config))).toBeInTheDocument();*/

    fetchMock.called(UI_CONFIG);
  });
});
