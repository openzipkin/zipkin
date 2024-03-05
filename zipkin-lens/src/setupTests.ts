/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
