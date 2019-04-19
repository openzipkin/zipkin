import React from 'react';
import { shallow } from 'enzyme';
import { Link } from 'react-router-dom';

import SidebarPageOption from './SidebarPageOption';

describe('<SidebarPageOption />', () => {
  it('should have proper components and classes when it is selected', () => {
    const wrapper = shallow(
      <SidebarPageOption
        location={{ pathname: '/zipkin' }}
        pageName="browser"
      />,
    );
    expect(wrapper.find('.sidebar__page-option').length).toEqual(1);
    expect(wrapper.find('.sidebar__page-option--selected').length).toEqual(1); // selected
  });

  it('should have proper components and classes when it is not selected', () => {
    const wrapper = shallow(
      <SidebarPageOption
        location={{ pathname: '/zipkin' }}
        pageName="dependencies"
      />,
    );
    expect(wrapper.find('.sidebar__page-option').length).toEqual(1);
    expect(wrapper.find('.sidebar__page-option--selected').length).toEqual(0); // not selected
  });

  it('should have proper path and label in Browser', () => {
    const wrapper = shallow(
      <SidebarPageOption
        location={{ pathname: '/zipkin' }}
        pageName="browser"
      />,
    );
    expect(wrapper.find(Link).filterWhere(
      item => item.prop('to').pathname === '/zipkin',
    ).length).toEqual(1);
    expect(wrapper.find('.sidebar__page-option-label').text()).toEqual('Search');
  });

  it('should have proper path and label in Dependencies', () => {
    const wrapper = shallow(
      <SidebarPageOption
        location={{ pathname: '/zipkin' }}
        pageName="dependencies"
      />,
    );
    expect(wrapper.find(Link).filterWhere(
      item => item.prop('to').pathname === '/zipkin/dependency',
    ).length).toEqual(1);
    expect(wrapper.find('.sidebar__page-option-label').text()).toEqual('Dependencies');
  });
});
