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
import { shallow } from 'enzyme';
import React from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';
import { ThemeProvider } from '@material-ui/styles';
import { MuiPickersUtilsProvider } from '@material-ui/pickers';

import App from './App';
import Layout from './Layout';

describe('<App />', () => {
  let wrapper;

  beforeEach(() => {
    wrapper = shallow(<App />);
  });

  it('should render Layout', () => {
    const items = wrapper.find(Layout);
    expect(items.length).toBe(1);
  });

  describe('should render router', () => {
    it('should render BrowserRouter', () => {
      const items = wrapper.find(BrowserRouter);
      expect(items.length).toBe(1);
    });

    it('should render 4 routes', () => {
      const items = wrapper.find(Route);
      expect(items.length).toBe(4);
    });
  });

  describe('should render providers', () => {
    it('should render MuiPickersUtilsProvider', () => {
      const items = wrapper.find(MuiPickersUtilsProvider);
      expect(items.length).toBe(1);
    });

    it('should render ThemeProvider', () => {
      const items = wrapper.find(ThemeProvider);
      expect(items.length).toBe(1);
    });

    it('should render redux Provider', () => {
      const items = wrapper.find(Provider);
      expect(items.length).toBe(1);
    });
  });
});
