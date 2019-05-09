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

import MiniTimelineLabel from './MiniTimelineLabel';

describe('<MiniTimelineLabel />', () => {
  it('should set proper positions', () => {
    const wrapper = shallow(<MiniTimelineLabel numTimeMarkers={5} duration={300} />);
    const labelWrappers = wrapper.find('[data-test="label-wrapper"]');
    expect(labelWrappers.at(0).prop('style')).toEqual({ left: '0%' });
    expect(labelWrappers.at(1).prop('style')).toEqual({ left: '25%' });
    expect(labelWrappers.at(2).prop('style')).toEqual({ left: '50%' });
    expect(labelWrappers.at(3).prop('style')).toEqual({ left: '75%' });
    expect(labelWrappers.at(4).prop('style')).toEqual({ left: '100%' });
  });

  it('should set proper modifiers', () => {
    const wrapper = shallow(<MiniTimelineLabel numTimeMarkers={5} duration={300} />);
    const labelWrappers = wrapper.find('[data-test="label"]');
    expect(labelWrappers.at(0).hasClass('mini-timeline-label__label--first'));
    expect(labelWrappers.at(1).hasClass('mini-timeline-label__label--first'));
    expect(labelWrappers.at(2).hasClass('mini-timeline-label__label--first'));
    expect(labelWrappers.at(3).hasClass('mini-timeline-label__label--first'));
    expect(labelWrappers.at(4).hasClass('mini-timeline-label__label--last'));
  });
});
