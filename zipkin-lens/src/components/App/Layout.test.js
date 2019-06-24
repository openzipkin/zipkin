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
import { shallow } from 'enzyme';
import CssBaseline from '@material-ui/core/CssBaseline';

import Layout from './Layout';
import Sidebar from './Sidebar';
import TraceIdSearchInput from './TraceIdSearchInput';
import TraceJsonUploader from './TraceJsonUploader';

describe('<Layout />', () => {
  let wrapper;

  beforeEach(() => {
    wrapper = shallow(
      <Layout.WrappedComponent>
        <div className="child-1" />
        <div className="child-2" />
        <div className="child-3" />
      </Layout.WrappedComponent>,
    );
  });

  it('should render CssBaseline', () => {
    const items = wrapper.find(CssBaseline);
    expect(items.length).toBe(1);
  });

  it('should render Sidebar', () => {
    const items = wrapper.find(Sidebar);
    expect(items.length).toBe(1);
  });

  it('should render page title', () => {
    const items = wrapper.find('[data-test="page-title"]');
    expect(items.length).toBe(1);
  });

  it('should render TraceIdSearchInput', () => {
    const items = wrapper.find(TraceIdSearchInput);
    expect(items.length).toBe(1);
  });

  it('should render TraceJsonUploader', () => {
    const items = wrapper.find(TraceJsonUploader);
    expect(items.length).toBe(1);
  });

  it('should render content\'s paper', () => {
    const items = wrapper.find('[data-test="content-paper"]');
    expect(items.length).toBe(1);
  });
});
