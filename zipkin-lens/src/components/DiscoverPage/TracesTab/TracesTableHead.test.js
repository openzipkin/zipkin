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
import React from 'react';

import render from '../../../test/util/render-with-default-settings';

import TracesTableHead from './TracesTableHead';
import { sortingMethods } from './util';

describe('<TracesTableHead />', () => {
  describe('should handle click event correctly', () => {
    let onSortingMethodChange;

    beforeEach(() => {
      onSortingMethodChange = jest.fn();
    });

    it('Duration cell -> StartTime cell', () => {
      const { getByTestId } = render(
        <TracesTableHead
          sortingMethod={sortingMethods.LONGEST_FIRST}
          onSortingMethodChange={onSortingMethodChange}
        />,
      );

      const startTimeCell = getByTestId('start-time');
      fireEvent.click(startTimeCell);
      expect(onSortingMethodChange).toHaveBeenCalledTimes(1);
      expect(onSortingMethodChange).toHaveBeenCalledWith(
        sortingMethods.NEWEST_FIRST,
      );
    });

    it('StartTime cell Newest -> StartTime cell Oldest', () => {
      const { getByTestId } = render(
        <TracesTableHead
          sortingMethod={sortingMethods.NEWEST_FIRST}
          onSortingMethodChange={onSortingMethodChange}
        />,
      );

      const startTimeCell = getByTestId('start-time');
      fireEvent.click(startTimeCell);
      expect(onSortingMethodChange).toHaveBeenCalledTimes(1);
      expect(onSortingMethodChange).toHaveBeenCalledWith(
        sortingMethods.OLDEST_FIRST,
      );
    });

    it('Duration cell Longest -> Duration cell Shortest', () => {
      const { getByTestId } = render(
        <TracesTableHead
          sortingMethod={sortingMethods.LONGEST_FIRST}
          onSortingMethodChange={onSortingMethodChange}
        />,
      );

      const durationCell = getByTestId('duration');
      fireEvent.click(durationCell);
      expect(onSortingMethodChange).toHaveBeenCalledTimes(1);
      expect(onSortingMethodChange).toHaveBeenCalledWith(
        sortingMethods.SHORTEST_FIRST,
      );
    });

    it('StartTime cell -> Duration cell', () => {
      const { getByTestId } = render(
        <TracesTableHead
          sortingMethod={sortingMethods.NEWEST_FIRST}
          onSortingMethodChange={onSortingMethodChange}
        />,
      );

      const durationCell = getByTestId('duration');
      fireEvent.click(durationCell);
      expect(onSortingMethodChange).toHaveBeenCalledTimes(1);
      expect(onSortingMethodChange).toHaveBeenCalledWith(
        sortingMethods.LONGEST_FIRST,
      );
    });
  });
});
