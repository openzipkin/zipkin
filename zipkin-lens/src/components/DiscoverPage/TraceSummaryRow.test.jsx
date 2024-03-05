/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect } from 'vitest';
import React from 'react';
import TraceSummaryRow from './TraceSummaryRow';
import render from '../../test/util/render-with-default-settings';
import { screen } from '@testing-library/react';

describe('<TraceSummaryRow />', () => {
  it('should render timestamp and duration in correct unit', () => {
    render(
      <table>
        <tbody>
          <TraceSummaryRow
            traceSummary={{
              traceId: 'a03ee8fff1dcd9b9',
              timestamp: 1571896375237354,
              duration: 131848,
              serviceSummaries: [],
              spanCount: 10,
              width: 10,
              root: {
                serviceName: 'routing',
                spanName: 'post /location/update/v4',
              },
            }}
          />
        </tbody>
      </table>,
    );

    const startTimeFormat = screen.getByTestId(
      'TraceSummaryRow-startTimeFormat',
    );
    expect(startTimeFormat).toBeDefined();
    // Don't assert on hour as the timezone will be different in CI
    expect(startTimeFormat.textContent).toMatch(
      /10\/2[34] [0-9][0-9]:52:55:237/,
    );
    // Intentionally not asserting the relative time from now as it would drift tests

    const duration = screen.getByTestId('TraceSummaryRow-duration');
    expect(duration).toBeDefined();
    expect(duration.textContent).toBe('131.848ms');
  });
});
