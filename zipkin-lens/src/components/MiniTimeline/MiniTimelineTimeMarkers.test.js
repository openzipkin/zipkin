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

import MiniTimelineTimeMarkers from './MiniTimelineTimeMarkers';

describe('<MiniTimelineTimeMarkers />', () => {
  it('should set proper positions', () => {
    const wrapper = shallow(<MiniTimelineTimeMarkers height={75} numTimeMarkers={5} />);
    const timeMarkers = wrapper.find('line');
    expect(timeMarkers.at(0).prop('x1')).toEqual('25%');
    expect(timeMarkers.at(1).prop('x1')).toEqual('50%');
    expect(timeMarkers.at(2).prop('x1')).toEqual('75%');
  });
});
