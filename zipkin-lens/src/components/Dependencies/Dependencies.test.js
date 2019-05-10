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

import { Dependencies } from './Dependencies';

describe('<Dependencies>', () => {
  it('should not fetch dependencies when location.search is empty', () => {
    const props = {
      location: {
        search: '',
      },
      isLoading: false,
      graph: {
        allNodes: () => ([]),
        getTargetEdges: () => ([]),
        getSourceEdges: () => ([]),
      },
      history: {
        push: () => {},
      },
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
      graph: {
        allNodes: () => ([]),
        getTargetEdges: () => ([]),
        getSourceEdges: () => ([]),
      },
      history: {
        push: () => {},
      },
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
      graph: {
        allNodes: () => ([]),
        getTargetEdges: () => ([]),
        getSourceEdges: () => ([]),
      },
      history: {
        push: () => {},
      },
      fetchDependencies: () => {},
      clearDependencies: jest.fn(),
    };
    const wrapper = shallow(<Dependencies {...props} />);
    wrapper.unmount();
    const { clearDependencies } = props;
    expect(clearDependencies.mock.calls.length).toBe(1);
  });
});
