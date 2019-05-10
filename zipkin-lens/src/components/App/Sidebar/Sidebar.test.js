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
import Cookies from 'js-cookie';

import Sidebar from './Sidebar';

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
