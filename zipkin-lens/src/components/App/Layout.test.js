import React from 'react';
import { shallow } from 'enzyme';

import Layout from './Layout';
import Sidebar from './Sidebar';
import GlobalSearchContainer from '../../containers/GlobalSearch/GlobalSearchContainer';

describe('<Layout />', () => {
  it('should have appropriate components and classes', () => {
    const wrapper = shallow(
      <Layout.WrappedComponent location={{}}>
        <div className="dummy1" />
        <div className="dummy2" />
        <div className="dummy3" />
      </Layout.WrappedComponent>,
    );
    expect(wrapper.find('.app__layout').length).toEqual(1);
    expect(wrapper.find('.app__global-search-wrapper').length).toEqual(1);
    expect(wrapper.find('.app__content').length).toEqual(1);
    expect(wrapper.find('.dummy1').length).toEqual(1);
    expect(wrapper.find('.dummy2').length).toEqual(1);
    expect(wrapper.find('.dummy3').length).toEqual(1);
    expect(wrapper.find(Sidebar).length).toEqual(1);
    expect(wrapper.find(GlobalSearchContainer).length).toEqual(1);
  });
});
