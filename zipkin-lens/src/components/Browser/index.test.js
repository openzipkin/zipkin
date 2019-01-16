import React from 'react';
import { shallow } from 'enzyme';

import Browser from './index';

describe('<Browser>', () => {
  it('should clear traces when unmounted', () => {
    const props = {
      location: {
        search: '',
      },
      clearTraces: jest.fn(),
      fetchTraces: () => {},
      traceSummaries: [],
      skewCorrectedTracesMap: {},
      isLoading: false,
    };
    const wrapper = shallow(<Browser {...props} />);
    wrapper.unmount();
    const { clearTraces } = props;
    expect(clearTraces.mock.calls.length).toBe(1);
  });
});
