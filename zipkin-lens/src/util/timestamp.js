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

export const formatDuration = (duration) => {
  if (duration === 0 || typeof duration === 'undefined') {
    return '0ms';
  }
  if (duration < 1000) {
    return `${duration}μs`;
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
