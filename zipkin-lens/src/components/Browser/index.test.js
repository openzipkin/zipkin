import React from 'react';
import { shallow } from 'enzyme';

import Browser from './index';
import BrowserHeader from './BrowserHeader';
import { sortingMethods } from './sorting';

describe('<Browser />', () => {
  const defaultProps = {
    location: {
      search: '',
    },
    clearTraces: jest.fn(),
    fetchTraces: () => {},
    traceSummaries: [],
    skewCorrectedTracesMap: {},
    isLoading: false,
  };

  it('should clear traces when unmounted', () => {
    const wrapper = shallow(<Browser {...defaultProps} />);
    wrapper.unmount();
    const { clearTraces } = defaultProps;
    expect(clearTraces.mock.calls.length).toBe(1);
  });

  it('should change state when sorting method is changed', () => {
    const wrapper = shallow(<Browser {...defaultProps} />);
    wrapper.find(BrowserHeader).prop('onChange')({
      value: sortingMethods.SHORTEST,
    });
    expect(wrapper.state('sortingMethod')).toEqual(sortingMethods.SHORTEST);
  });
});
