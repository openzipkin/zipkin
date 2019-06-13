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

import TraceSummaryBar from './TraceSummaryBar';

describe('<TraceSummaryBar />', () => {
  it('should render the correct width and color', () => {
    const wrapper = shallow(
      <TraceSummaryBar
        width={10}
        infoClass="trace-error-transient"
      >
        <div />
      </TraceSummaryBar>,
    );
    expect(wrapper.find('[data-test="bar-wrapper"]').prop('style')).toEqual({
      width: '10%',
    });
    expect(wrapper.find('[data-test="bar"]').prop('style')).toEqual({
      backgroundColor: '#da8b8b',
    });
  });
});
