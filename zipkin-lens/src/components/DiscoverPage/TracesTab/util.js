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
export const sortingMethods = {
  LONGEST_FIRST: 'LONGEST_FIRST',
  SHORTEST_FIRST: 'SHORTEST_FIRST',
  NEWEST_FIRST: 'NEWEST_FIRST',
  OLDEST_FIRST: 'OLDEST_FIRST',
};

export const sortTraceSummaries = (traceSummaries, sortingMethod) => {
  const copied = [...traceSummaries];
  return copied.sort((a, b) => {
    switch (sortingMethod) {
      case sortingMethods.LONGEST_FIRST:
        return b.duration - a.duration;
      case sortingMethods.SHORTEST_FIRST:
        return a.duration - b.duration;
      case sortingMethods.NEWEST_FIRST:
        return b.timestamp - a.timestamp;
      case sortingMethods.OLDEST_FIRST:
        return a.timestamp - b.timestamp;
      default:
        return 0;
    }
  });
};

export const extractAllServiceNames = (traceSummaries) => {
  const result = [];
  traceSummaries.forEach((traceSummary) => {
    traceSummary.serviceSummaries.forEach((serviceSummary) => {
      result.push(serviceSummary.serviceName);
    });
  });
  return Array.from(new Set(result)); // For uniqueness
};

export const filterTraceSummaries = (traceSummaries, filters) =>
  traceSummaries.filter((traceSummary) => {
    for (let i = 0; i < filters.length; i += 1) {
      if (
        !traceSummary.serviceSummaries.find(
          (serviceSummary) => serviceSummary.serviceName === filters[i],
        )
      ) {
        return false;
      }
    }
    return true;
  });
