/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import React from 'react';
import { mount } from 'enzyme';

import TracesTableHead from './TracesTableHead';
import { sortingMethods } from './util';

describe('<TracesTableHead />', () => {
  describe('should handle click event correctly', () => {
    let wrapper;
    let onSortingMethodChange;

    beforeEach(() => {
      onSortingMethodChange = jest.fn();
      wrapper = mount(
        <TracesTableHead
          sortingMethod={sortingMethods.LONGEST_FIRST}
          onSortingMethodChange={onSortingMethodChange}
        />,
      );
    });

    it('Duration cell -> StartTime cell', () => {
      const startTimeCell = wrapper.find('[data-test="start-time"]').first();
      startTimeCell.simulate('click');
      expect(onSortingMethodChange.mock.calls.length).toBe(1);
      expect(onSortingMethodChange.mock.calls[0][0]).toBe(sortingMethods.NEWEST_FIRST);
    });

    it('StartTime cell -> StartTime cell', () => {
      wrapper = mount(
        <TracesTableHead
          onSortingMethodChange={onSortingMethodChange}
          sortingMethod={sortingMethods.NEWEST_FIRST}
        />,
      );
      const startTimeCell = wrapper.find('[data-test="start-time"]').first();
      startTimeCell.simulate('click');
      expect(onSortingMethodChange.mock.calls.length).toBe(1);
      expect(onSortingMethodChange.mock.calls[0][0]).toBe(sortingMethods.OLDEST_FIRST);
    });

    it('Duration cell -> Duration cell', () => {
      const durationCell = wrapper.find('[data-test="duration"]').first();
      durationCell.simulate('click');
      expect(onSortingMethodChange.mock.calls.length).toBe(1);
      expect(onSortingMethodChange.mock.calls[0][0]).toBe(sortingMethods.SHORTEST_FIRST);
    });

    it('StartTime cell -> Duration cell', () => {
      wrapper = mount(
        <TracesTableHead
          onSortingMethodChange={onSortingMethodChange}
          sortingMethod={sortingMethods.NEWEST_FIRST}
        />,
      );
      const durationCell = wrapper.find('[data-test="duration"]').first();
      durationCell.simulate('click');
      expect(onSortingMethodChange.mock.calls.length).toBe(1);
      expect(onSortingMethodChange.mock.calls[0][0]).toBe(sortingMethods.LONGEST_FIRST);
    });
  });
});
