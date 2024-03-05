/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import moment from 'moment';

export const formatDuration = (duration) => {
  if (duration === 0 || typeof duration === 'undefined') {
    return '0ms';
  }
  if (duration < 1000) {
    return `${duration.toFixed(3)}μs`;
  }
  if (duration < 1000000) {
    return `${(duration / 1000).toFixed(3)}ms`;
  }
  return `${(duration / 1000000).toFixed(3)}s`;
};

export const formatTimestamp = (timestamp) =>
  moment(timestamp / 1000).format('MM/DD HH:mm:ss.SSS');

// moment.js only supports millisecond precision, however our timestamps have
// microsecond precision. So we use moment.js to generate the human readable time
// with just milliseconds and then append the last 3 digits of the timestamp
// which are the microseconds.
// NOTE: a.timestamp % 1000 would save a string conversion but drops leading zeros.
export const formatTimestampMicros = (timestamp) =>
  `${formatTimestamp(timestamp)}_${timestamp.toString().slice(-3)}`;
