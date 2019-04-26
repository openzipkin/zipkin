/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
export const sortingMethods = {
  LONGEST: 'LONGEST',
  SHORTEST: 'SHORTEST',
  NEWEST: 'NEWEST',
  OLDEST: 'OLDEST',
};

export const sortingMethodOptions = [
  { value: sortingMethods.LONGEST, label: 'Longest First' },
  { value: sortingMethods.SHORTEST, label: 'Shortest First' },
  { value: sortingMethods.NEWEST, label: 'Newest First' },
  { value: sortingMethods.OLDEST, label: 'Oldest First' },
];

export const sortTraceSummaries = (traceSummaries, sortingMethod) => {
  const copied = [...traceSummaries];
  return copied.sort((a, b) => {
    switch (sortingMethod) {
      case sortingMethods.LONGEST:
        return b.duration - a.duration;
      case sortingMethods.SHORTEST:
        return a.duration - b.duration;
      case sortingMethods.NEWEST:
        return b.timestamp - a.timestamp;
      case sortingMethods.OLDEST:
        return a.timestamp - b.timestamp;
      default:
        return 0;
    }
  });
};
