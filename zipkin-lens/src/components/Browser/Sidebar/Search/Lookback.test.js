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
