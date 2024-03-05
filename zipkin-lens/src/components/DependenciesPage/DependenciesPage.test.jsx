/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import fetchMock from 'fetch-mock';
// @ts-ignore
import { createMemoryHistory } from 'history';
import moment from 'moment';
import React from 'react';

import DependenciesPage from './DependenciesPage';
import render from '../../test/util/render-with-default-settings';

vi.mock('@material-ui/pickers', () => {
  // eslint-disable-next-line
  const moment = require('moment');
  return {
    // eslint-disable-next-line react/prop-types
    KeyboardDateTimePicker: ({ value, onChange }) => (
      <input
        // eslint-disable-next-line react/prop-types
        value={value.format('MM/DD/YYYY HH:mm:ss')}
        onChange={(event) => onChange(moment(event.target.value))}
        data-testid="date-time-picker"
      />
    ),
  };
});

// vizceral uses setTimeout internally, so if you use jest.runAllTimers
// in the test, problems will occur.
// To avoid it, mock Vizceral (VizceralWrapper).
vi.mock('./VizceralWrapper', () => ({
  default: () => <div />,
}));

vi.useFakeTimers();

describe('<DependenciesPage />', () => {
  afterEach(cleanup);
  it('should manage the temporary time range with DateTimePicker and reflect it in the URL when the search button is clicked', () => {
    const history = createMemoryHistory();
    render(<DependenciesPage />, {
      history,
    });

    const dateTimePickers = screen.getAllByTestId('date-time-picker');
    const [startDateTimePicker, endDateTimePicker] = dateTimePickers;

    const startTimeStr = '2013-02-08 09:30:26';
    const endTimeStr = '2013-02-09 10:40:45';
    const startTime = moment(startTimeStr);
    const endTime = moment(endTimeStr);

    fireEvent.change(startDateTimePicker, {
      target: { value: startTimeStr },
    });
    expect(startDateTimePicker.value).toBe('02/08/2013 09:30:26');

    fireEvent.change(endDateTimePicker, {
      target: { value: endTimeStr },
    });
    expect(endDateTimePicker.value).toBe('02/09/2013 10:40:45');

    // When the search button is clicked, reflect the temp time range changes to URL search params.
    fireEvent.click(screen.getByTestId('search-button'));
    const params = new URLSearchParams(history.location.search);
    expect(params.get('startTime')).toBe(startTime.valueOf().toString());
    expect(params.get('endTime')).toBe(endTime.valueOf().toString());
  });

  it('should fetch or clear dependencies when URL is changed', async () => {
    fetchMock.get('*', [
      {
        parent: 'serviceA',
        child: 'serverB',
        callCount: 10,
        errorCount: 20,
      },
    ]);

    const history = createMemoryHistory();
    const { rerender } = render(<DependenciesPage />, {
      history,
    });

    // When the query parameter is set, dependencies will be fetched
    // and loading-indicator will be displayed.
    history.push({
      location: '/dependencies',
      search: '?startTime=1586268120000&endTime=1587132132201',
    });
    rerender(
      <DependenciesPage history={history} location={history.location} />,
    );
    expect(screen.getAllByTestId('loading-indicator').length).toBe(1);

    // Because setTimeout is used in the action creator that fetches dependencies,
    // use jest.runAllTimers to complete all timers.
    vi.runAllTimers();
    // If wait a while after loading-indicator is displayed,
    // dependencies-graph will appear.
    const components = await screen.findAllByTestId('dependencies-graph');
    expect(components.length).toBe(1);
  });
});
