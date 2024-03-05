/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import moment from 'moment';
import React from 'react';

import LookbackMenu from './LookbackMenu';
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

describe('<LookbackMenu />', () => {
  afterEach(cleanup);

  it('should change lookback and close when a list item is clicked', () => {
    const lookback = {
      type: 'fixed',
      value: '15m',
      endTime: moment(),
    };

    const close = vi.fn();
    const onChange = vi.fn();

    render(
      <LookbackMenu close={close} onChange={onChange} lookback={lookback} />,
    );

    fireEvent.click(screen.getByTestId('lookback-2h'));

    expect(onChange.mock.calls.length).toBe(1);
    expect(onChange.mock.calls[0][0].type).toBe('fixed');
    expect(onChange.mock.calls[0][0].value).toBe('2h');
    expect(close.mock.calls.length).toBe(1);
  });

  it('should change lookback and close when Apply button is clicked', () => {
    const lookback = {
      type: 'fixed',
      value: '15m',
      endTime: moment(),
    };

    const close = vi.fn();
    const onChange = vi.fn();

    render(
      <LookbackMenu close={close} onChange={onChange} lookback={lookback} />,
    );

    const dateTimePickers = screen.getAllByTestId('date-time-picker');
    const [startDateTimePicker, endDateTimePicker] = dateTimePickers;

    const startTimeStr = '2013-02-08 09:30:26';
    const endTimeStr = '2013-02-09 10:40:45';
    const startTime = moment(startTimeStr);
    const endTime = moment(endTimeStr);

    // Change date time pickers' state.
    fireEvent.change(startDateTimePicker, {
      target: { value: startTimeStr },
    });
    fireEvent.change(endDateTimePicker, {
      target: { value: endTimeStr },
    });

    fireEvent.click(screen.getByTestId('apply-button'));
    expect(onChange.mock.calls.length).toBe(1);
    expect(onChange.mock.calls[0][0].type).toBe('range');
    expect(onChange.mock.calls[0][0].endTime.valueOf()).toBe(endTime.valueOf());
    expect(onChange.mock.calls[0][0].startTime.valueOf()).toBe(
      startTime.valueOf(),
    );
    expect(close.mock.calls.length).toBe(1);
  });

  it('should change lookback and close when Millis Apply button is clicked', () => {
    const lookback = {
      type: 'fixed',
      value: '15m',
      endTime: moment(),
    };

    const close = vi.fn();
    const onChange = vi.fn();

    render(
      <LookbackMenu close={close} onChange={onChange} lookback={lookback} />,
    );
    const millisInput = screen.getByTestId('millis-input');

    fireEvent.change(millisInput, {
      target: { value: '12345' },
    });

    fireEvent.click(screen.getByTestId('millis-apply-button'));
    expect(onChange.mock.calls.length).toBe(1);
    expect(onChange.mock.calls[0][0].type).toBe('millis');
    expect(onChange.mock.calls[0][0].value).toBe(12345);
    expect(close.mock.calls.length).toBe(1);
  });

  it('should close when click outside', () => {
    const lookback = {
      type: 'fixed',
      value: '15m',
      endTime: moment(),
    };

    const close = vi.fn();
    const onChange = vi.fn();

    render(
      <LookbackMenu close={close} onChange={onChange} lookback={lookback} />,
    );

    // Click outside of the component.
    fireEvent.click(document);
    expect(close.mock.calls.length).toBe(0);
    expect(onChange.mock.calls.length).toBe(0); // onChange must not be called.
  });

  // TODO: Test that `close` is not invoked when
  // DateTimePicker dialog is clicked.
});
