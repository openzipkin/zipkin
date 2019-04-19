import React from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';
import { shallow } from 'enzyme';

import App from './index';
import Layout from './Layout';

describe('<App />', () => {
  it('should have appropriate components', () => {
    const wrapper = shallow(<App />);
    expect(wrapper.find(Provider).length).toEqual(1);
    expect(wrapper.find(BrowserRouter).length).toEqual(1);
    expect(wrapper.find(Layout).length).toEqual(1);
    expect(wrapper.find(Route).length).toEqual(4);
  });
});
