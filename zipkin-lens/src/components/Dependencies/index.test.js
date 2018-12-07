import React from 'react';
import { shallow } from 'enzyme';

import Dependencies from './index';

describe('<Dependencies>', () => {
  it('should not fetch dependencies when location.search is empty', () => {
    const props = {
      location: {
        search: '',
      },
      isLoading: false,
      dependencies: [],
      fetchDependencies: jest.fn(),
      clearDependencies: () => {},
    };
    shallow(<Dependencies {...props} />);
    const { fetchDependencies } = props;
    expect(fetchDependencies.mock.calls.length).toBe(0);
  });

  it('should fetch dependencies when location.search is not empty', () => {
    const props = {
      location: {
        search: '?endTs=1542620031053',
      },
      isLoading: false,
      dependencies: [],
      fetchDependencies: jest.fn(),
      clearDependencies: () => {},
    };
    shallow(<Dependencies {...props} />);
    const { fetchDependencies } = props;
    expect(fetchDependencies.mock.calls.length).toBe(1);
  });

  it('should clear dependencies when unmounted', () => {
    const props = {
      location: {
        search: '',
      },
      isLoading: false,
      dependencies: [],
      fetchDependencies: () => {},
      clearDependencies: jest.fn(),
    };
    const wrapper = shallow(<Dependencies {...props} />);
    wrapper.unmount();
    const { clearDependencies } = props;
    expect(clearDependencies.mock.calls.length).toBe(1);
  });
});
