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
import { ThemeProvider } from '@material-ui/styles';
import { createMount, createShallow } from '@material-ui/core/test-utils';

import TimeMarker from './TimeMarker';
import { theme } from '../../../colors';

describe('<TimeMarker />', () => {
  let shallow;

  beforeEach(() => {
    shallow = createShallow();
  });

  it('should mount render to increase test coverage...', () => {
    const mount = createMount();
    mount(
      <ThemeProvider theme={theme}>
        <TimeMarker startTs={10} endTs={100} classes={{}} />
      </ThemeProvider>,
    );
  });

  it('should render markers correctly', () => {
    // |-----------|-----------|-----------|
    // 10μs        40μs        70μs       100μs
    const wrapper = shallow(<TimeMarker.Naked startTs={10} endTs={100} classes={{}} />);

    const markers = wrapper.find('[data-testid="time-marker--marker"]');
    expect(markers.length).toBe(4);
    expect(markers.at(0).props().style.left).toBe('0%');
    expect(Number(markers.at(1).props().style.left.slice(0, -1))).toBeCloseTo(100 / 3);
    expect(Number(markers.at(2).props().style.left.slice(0, -1))).toBeCloseTo(200 / 3);
    expect(markers.at(3).props().style.left).toBe('100%');
  });

  it('should render labels correctly', () => {
    // |-----------|-----------|-----------|
    // 10μs        40μs        70μs       100μs
    const wrapper = shallow(<TimeMarker.Naked startTs={10} endTs={100} classes={{}} />);

    const labels = wrapper.find('[data-testid="time-marker--label"]');
    expect(labels.length).toBe(4);
    expect(labels.at(0).text()).toBe('10μs');
    expect(labels.at(1).text()).toBe('40μs');
    expect(labels.at(2).text()).toBe('70μs');
    expect(labels.at(3).text()).toBe('100μs');
  });
});
