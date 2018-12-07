import React from 'react';
import { shallow } from 'enzyme';

import Browser from './index';

describe('<Browser>', () => {
  it('should not fetch traces when location.search is empty', () => {
    const props = {
      location: {
        search: '',
      },
      clearTraces: () => {},
      fetchTraces: jest.fn(),
    };
    shallow(<Browser {...props} />);
    const { fetchTraces } = props;
    expect(fetchTraces.mock.calls.length).toBe(0);
  });

  it('should fetch traces when location.search is not empty', () => {
    const props = {
      location: {
        search: '?serviceName=serviceA',
      },
      clearTraces: () => {},
      fetchTraces: jest.fn(),
    };
    shallow(<Browser {...props} />);
    const { fetchTraces } = props;
    expect(fetchTraces.mock.calls.length).toBe(1);
  });

  it('should fetch traces when location is changed', () => {
    const props = {
      location: {
        search: '?serviceName=serviceA',
      },
      clearTraces: () => {},
      fetchTraces: jest.fn(),
    };
    const wrapper = shallow(<Browser {...props} />);
    wrapper.setProps({
      location: {
        search: '?serviceName=serviceB',
      },
    });
    const { fetchTraces } = props;
    expect(fetchTraces.mock.calls.length).toBe(2);
  });

  it('should clear traces when unmounted', () => {
    const props = {
      location: {
        search: '',
      },
      clearTraces: jest.fn(),
      fetchTraces: () => {},
    };
    const wrapper = shallow(<Browser {...props} />);
    wrapper.unmount();
    const { clearTraces } = props;
    expect(clearTraces.mock.calls.length).toBe(1);
  });
});
