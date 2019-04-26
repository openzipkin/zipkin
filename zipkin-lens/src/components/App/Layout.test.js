/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
