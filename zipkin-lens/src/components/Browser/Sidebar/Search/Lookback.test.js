/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import React from 'react';
import { shallow } from 'enzyme';
import moment from 'moment';

import Lookback from './Lookback';

describe('<Lookback>', () => {
  it('should calculate lookback correctly when toDate is changed', () => {
    const props = {
      onEndTsChange: jest.fn(),
      onLookbackChange: jest.fn(),
    };
    const wrapper = shallow(<Lookback {...props} />);
    wrapper.setState({
      fromDate: moment(1542617684000),
      toDate: moment(1542617684100),
    });
    wrapper.instance().handleToDateChange(moment(1542617685000));

    const { onLookbackChange } = props;
    expect(onLookbackChange).toHaveBeenCalledWith(1000);
  });

  it('should calculate lookback correctly when fromDate is changed', () => {
    const props = {
      onEndTsChange: jest.fn(),
      onLookbackChange: jest.fn(),
    };
    const wrapper = shallow(<Lookback {...props} />);
    wrapper.setState({
      fromDate: moment(1542617684000),
      toDate: moment(1542617684000),
    });
    wrapper.instance().handleFromDateChange(moment(1542617683500));

    const { onLookbackChange } = props;
    expect(onLookbackChange).toHaveBeenCalledWith(500);
  });
});
