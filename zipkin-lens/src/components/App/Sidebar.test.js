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
import Drawer from '@material-ui/core/Drawer';

import Sidebar from './Sidebar';
import Logo from '../../img/zipkin-sm-logo.svg';

describe('<Sidebar />', () => {
  let wrapper;

  beforeEach(() => {
    wrapper = shallow(<Sidebar />);
  });

  it('should render Drawer', () => {
    const items = wrapper.find(Drawer);
    expect(items.length).toBe(1);
  });

  it('should render Logo', () => {
    const items = wrapper.find(Logo);
    expect(items.length).toBe(1);
  });

  describe('should render internal links', () => {
    let internalLinks;

    beforeEach(() => {
      internalLinks = wrapper.find('[data-test="internal-links"]');
    });

    it('should render 2 links', () => {
      expect(internalLinks.children().length).toBe(2);
    });

    it('should not pass isExternalLink', () => {
      internalLinks.forEach((internalLink) => {
        expect(internalLink.props().isExternalLink).toBeUndefined();
      });
    });
  });

  describe('should render external links', () => {
    let externalLinks;

    beforeEach(() => {
      externalLinks = wrapper.find('[data-test="external-links"]');

      it('should render 4 links', () => {
        expect(externalLinks.children().length).toBe(4);
      });

      it('should pass isExternalLink', () => {
        externalLinks.forEach((externalLink) => {
          expect(externalLink.props().isExternalLink).toBe(true);
        });
      });
    });
  });
});
