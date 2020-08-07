/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import { fireEvent } from '@testing-library/react';
import moment from 'moment';
import React from 'react';

import LookbackMenu from './LookbackMenu';
import render from '../../test/util/render-with-default-settings';

jest.mock('@material-ui/pickers', () => {
  // eslint-disable-next-line global-require,no-shadow
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
  it('should change lookback and close when a list item is clicked', () => {
    const lookback = {
      type: 'fixed',
      value: '15m',
      endTime: moment(),
    };

    const close = jest.fn();
    const onChange = jest.fn();

    const { getByTestId } = render(
      <LookbackMenu close={close} onChange={onChange} lookback={lookback} />,
    );

    fireEvent.click(getByTestId('lookback-2h'));

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

    const close = jest.fn();
    const onChange = jest.fn();

    const { getAllByTestId, getByTestId } = render(
      <LookbackMenu close={close} onChange={onChange} lookback={lookback} />,
    );

    const dateTimePickers = getAllByTestId('date-time-picker');
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

    fireEvent.click(getByTestId('apply-button'));
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

    const close = jest.fn();
    const onChange = jest.fn();

    const { getByTestId } = render(
      <LookbackMenu close={close} onChange={onChange} lookback={lookback} />,
    );
    const millisInput = getByTestId('millis-input');

    fireEvent.change(millisInput, {
      target: { value: '12345' },
    });

    fireEvent.click(getByTestId('millis-apply-button'));
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

    const close = jest.fn();
    const onChange = jest.fn();

    render(
      <LookbackMenu close={close} onChange={onChange} lookback={lookback} />,
    );

    // Click outside of the component.
    fireEvent.click(document);

    expect(close.mock.calls.length).toBe(1);
    expect(onChange.mock.calls.length).toBe(0); // onChange must not be called.
  });

  // TODO: Test that `close` is not invoked when
  // DateTimePicker dialog is clicked.
});
