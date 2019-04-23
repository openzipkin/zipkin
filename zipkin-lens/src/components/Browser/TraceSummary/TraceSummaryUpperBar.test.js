import React from 'react';
import { shallow } from 'enzyme';

import TraceSummaryUpperBar from './TraceSummaryUpperBar';

describe('<TraceSummaryUpperBar />', () => {
  it('should render component correctly', () => {
    const traceSummary = {
      traceId: '1',
      width: 10,
      infoClass: '',
      duration: 3,
      durationStr: '3μs',
      spanCount: 10,
      timestamp: 1555936837862000,
      serviceSummaries: [],
    };
    const wrapper = shallow(<TraceSummaryUpperBar traceSummary={traceSummary} />);
    expect(wrapper.find('[data-test="duration"]').text()).toEqual('3μs');
    expect(wrapper.find('[data-test="spans"]').text()).toEqual('10 spans');
  });
});
