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
import React from 'react';
import { shallow } from 'enzyme';
import Slider from 'rc-slider';

import MiniTimelineSlider from './MiniTimelineSlider';

const { Range } = Slider;

describe('<MiniTimelineSlider />', () => {
  it('should change isDragging state before changing the range', () => {
    const wrapper = shallow(
      <MiniTimelineSlider
        duration={10}
        startTs={0}
        endTs={10}
        onStartAndEndTsChange={() => {}}
      />,
    );
    wrapper.find(Range).prop('onBeforeChange')();
    expect(wrapper.state('isDragging')).toEqual(true);
  });

  it('should change isDragging state after changing the range', () => {
    const wrapper = shallow(
      <MiniTimelineSlider
        duration={10}
        startTs={0}
        endTs={10}
        onStartAndEndTsChange={() => {}}
      />,
    );
    wrapper.find(Range).prop('onBeforeChange')(); // isDragging === true
    wrapper.find(Range).prop('onAfterChange')([2, 6]);
    expect(wrapper.state('isDragging')).toEqual(false);
  });

  it('should call onStartAndEndTsChange after range change', () => {
    const onStartAndEndTsChange = jest.fn();
    const wrapper = shallow(
      <MiniTimelineSlider
        duration={10}
        startTs={0}
        endTs={10}
        onStartAndEndTsChange={onStartAndEndTsChange}
      />,
    );
    wrapper.find(Range).prop('onAfterChange')([2, 6]);
    expect(onStartAndEndTsChange).toHaveBeenCalledWith(
      2 / 10,
      6 / 10,
    );
  });
});
