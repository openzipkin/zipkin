import React from 'react';
import { shallow } from 'enzyme';
import Cookies from 'js-cookie';

import Sidebar from './index';

describe('<Sidebar />', () => {
  it('should have proper classes', () => {
    const wrapper = shallow(
      <Sidebar.WrappedComponent
        location={{ pathname: '' }}
        history={{ push: () => {} }}
      />,
    );
    expect(wrapper.find('.sidebar').length).toEqual(1);
  });

  it('should have goBackToClassic button when cookie is true', () => {
    Cookies.get = jest.fn().mockImplementation(() => true);
    const wrapper = shallow(
      <Sidebar.WrappedComponent
        location={{ pathname: '' }}
        history={{ push: () => {} }}
      />,
    );
    expect(wrapper.find('.sidebar__go-back-to-classic-button-wrapper').length).toEqual(1);
  });

  it('should not have goBackToClassic button when cookie is false', () => {
    Cookies.get = jest.fn().mockImplementation(() => false);
    const wrapper = shallow(
      <Sidebar.WrappedComponent
        location={{ pathname: '' }}
        history={{ push: () => {} }}
      />,
    );
    expect(wrapper.find('.sidebar__go-back-to-classic-button-wrapper').length).toEqual(0);
  });

  it('should push "/zipkin/" in goBackToClassic when location.pathname is "/zipkin"', () => {
    const pushSpy = jest.fn();
    const wrapper = shallow(
      <Sidebar.WrappedComponent
        location={{ pathname: '/zipkin' }}
        history={{ push: pushSpy }}
      />,
    );
    wrapper.instance().goBackToClassic({
      preventDefault: () => {},
    });
    expect(pushSpy).toHaveBeenCalledWith('/zipkin/');
  });

  it('should push same pathname if location.pathname is not "/zipkin"', () => {
    const pushSpy = jest.fn();
    const wrapper = shallow(
      <Sidebar.WrappedComponent
        location={{ pathname: '/zipkin/dependency' }}
        history={{ push: pushSpy }}
      />,
    );
    wrapper.instance().goBackToClassic({
      preventDefault: () => {},
    });
    expect(pushSpy).toHaveBeenCalledWith('/zipkin/dependency');
  });
});
