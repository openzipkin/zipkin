/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import React from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';
import { ThemeProvider } from '@material-ui/styles';
import { MuiPickersUtilsProvider } from '@material-ui/pickers';
import { createMount, createShallow } from '@material-ui/core/test-utils';

import App from './App';
import Layout from './Layout';
import DiscoverPage from '../DiscoverPage';
import TracePage from '../TracePage';
import { theme } from '../../colors';

describe('<App />', () => {
  let mount;
  let shallow;

  beforeEach(() => {
    mount = createMount({ strict: true });
    shallow = createShallow();
  });

  afterEach(() => {
    mount.cleanUp();
  });

  it('should update document title', () => {
    mount(<App />);
    expect(document.title).toBe('Zipkin');
  });

  it('should render providers', () => {
    const wrapper = shallow(<App />);
    expect(wrapper.find(MuiPickersUtilsProvider).length).toBe(1);
    expect(wrapper.find(ThemeProvider).length).toBe(1);
    expect(wrapper.find(ThemeProvider).props().theme).toEqual(theme);
    expect(wrapper.find(Provider).length).toBe(1);
  });

  it('should render Router as a parent of Layout', () => {
    const wrapper = shallow(<App />);
    expect(wrapper.find(Layout).parent().type()).toEqual(BrowserRouter);
  });

  it('should render Layout', () => {
    const wrapper = shallow(<App />);
    expect(wrapper.find(Layout).length).toBe(1);
  });

  it('should render Route', () => {
    const wrapper = shallow(<App />);
    const routes = wrapper.find(Route);
    expect(routes.at(0).props().path).toEqual(['/zipkin', '/zipkin/dependency']);
    expect(routes.at(0).props().component).toEqual(DiscoverPage);
    expect(routes.at(1).props().path).toEqual(['/zipkin/traces/:traceId', '/zipkin/traceViewer']);
    expect(routes.at(1).props().component).toEqual(TracePage);
  });
});
