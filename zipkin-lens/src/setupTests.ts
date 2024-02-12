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
import '@testing-library/jest-dom';
import fetchMock from 'fetch-mock';

import { beforeAll, beforeEach, vi, afterAll } from 'vitest';
import { UI_CONFIG } from './constants/api';

// Mock out UI Config fetch with an empty config as a baseline, tests can remock as needed.
fetchMock.mock(UI_CONFIG, {});

// Mock out browser refresh.
const { location } = window;

// Allow redefining browser language.
const { language } = window.navigator;

beforeAll(() => {
  window.location = {
    ...location,
    reload: vi.fn(),
  } as any; // Only partial mock so don't enforce full type.
});

beforeEach(() => {
  // Set english as browser locale by default.
  Object.defineProperty(window.navigator, 'language', {
    value: 'en-US',
    configurable: true,
  });
});

afterAll(() => {
  // Restore overrides for good measure.
  Object.defineProperty(window.navigator, 'language', {
    value: language,
    configurable: true,
  });
  window.location = location;
});
