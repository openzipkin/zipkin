import React from 'react';
import { shallow } from 'enzyme';

import Traces from './index';
import InitialMessage from './InitialMessage';

describe('<Traces>', () => {
  it('should show InitialMessage if location.search is empty', () => {
    const props = {
      isLoading: false,
      clockSkewCorrectedTracesMap: {},
      traceSummaries: [],
      location: {
        search: '',
      },
    };
    const wrapper = shallow(<Traces {...props} />);
    expect(wrapper.find(InitialMessage).length).toBe(1);
    expect(wrapper.find('.traces').length).toBe(0);
  });

  it('should show Traces if location.search is not empty', () => {
    const props = {
      isLoading: false,
      clockSkewCorrectedTracesMap: {},
      traceSummaries: [],
      location: {
        search: '?serviceName=serviceA',
      },
    };
    const wrapper = shallow(<Traces {...props} />);
    expect(wrapper.find(InitialMessage).length).toBe(0);
    expect(wrapper.find('.traces').length).toBe(1);
  });
});
