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

import moment from 'moment';

export type FixedLookbackValue =
  | '1m'
  | '5m'
  | '15m'
  | '30m'
  | '1h'
  | '2h'
  | '3h'
  | '6h'
  | '12h'
  | '1d'
  | '2d'
  | '7d';

export const millisecondsToValue: { [key: number]: FixedLookbackValue } = {
  [60 * 1000]: '1m',
  [60 * 1000 * 5]: '5m',
  [60 * 1000 * 15]: '15m',
  [60 * 1000 * 30]: '30m',
  [60 * 1000 * 60]: '1h',
  [60 * 1000 * 60 * 2]: '2h',
  [60 * 1000 * 60 * 3]: '3h',
  [60 * 1000 * 60 * 6]: '6h',
  [60 * 1000 * 60 * 12]: '12h',
  [60 * 1000 * 60 * 24]: '1d',
  [60 * 1000 * 60 * 24 * 2]: '2d',
  [60 * 1000 * 60 * 24 * 7]: '7d',
};

export interface FixedLookback {
  type: 'fixed';
  value: FixedLookbackValue;
  endTime: moment.Moment;
}

export interface RangeLookback {
  type: 'range';
  startTime: moment.Moment;
  endTime: moment.Moment;
}

export interface MillisLookback {
  type: 'millis';
  value: number;
  endTime: moment.Moment;
}

export type Lookback = FixedLookback | RangeLookback | MillisLookback;

interface FixedLookbackEntry {
  duration: moment.Duration;
  value: FixedLookbackValue;
  display: string;
}

export const fixedLookbacks: Array<FixedLookbackEntry> = [
  {
    duration: moment.duration({ minutes: 1 }),
    value: '1m',
    display: 'Last 1 minute',
  },
  {
    duration: moment.duration({ minutes: 5 }),
    value: '5m',
    display: 'Last 5 minutes',
  },
  {
    duration: moment.duration({ minutes: 15 }),
    value: '15m',
    display: 'Last 15 minutes',
  },
  {
    duration: moment.duration({ minutes: 30 }),
    value: '30m',
    display: 'Last 30 minutes',
  },
  {
    duration: moment.duration({ hours: 1 }),
    value: '1h',
    display: 'Last 1 hour',
  },
  {
    duration: moment.duration({ hours: 2 }),
    value: '2h',
    display: 'Last 2 hours',
  },
  {
    duration: moment.duration({ hours: 3 }),
    value: '3h',
    display: 'Last 3 hours',
  },
  {
    duration: moment.duration({ hours: 6 }),
    value: '6h',
    display: 'Last 6 hours',
  },
  {
    duration: moment.duration({ hours: 12 }),
    value: '12h',
    display: 'Last 12 hours',
  },
  {
    duration: moment.duration({ days: 1 }),
    value: '1d',
    display: 'Last 1 day',
  },
  {
    duration: moment.duration({ days: 2 }),
    value: '2d',
    display: 'Last 2 days',
  },
  {
    duration: moment.duration({ days: 7 }),
    value: '7d',
    display: 'Last 7 days',
  },
];

export const fixedLookbackMap = fixedLookbacks.reduce((acc, cur) => {
  acc[cur.value] = cur;
  return acc;
}, {} as { [key: string]: FixedLookbackEntry });
