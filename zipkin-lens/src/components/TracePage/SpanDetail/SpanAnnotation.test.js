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
import { createShallow } from '@material-ui/core/test-utils';

import SpanAnnotation from './SpanAnnotation';

describe('<SpanAnnotation />', () => {
  let shallow;

  beforeEach(() => {
    shallow = createShallow();
  });

  it('should render annotation data', () => {
    const wrapper = shallow(
      <SpanAnnotation.Naked
        annotation={{
          value: 'Server Start',
          timestamp: 1543334627716006,
          relativeTime: '700ms',
          endpoint: '127.0.0.1',
        }}
        classes={{}}
      />,
    );
    const rows = wrapper.find('[data-testid="span-annotation--table-body"]');
    expect(rows.childAt(0).find('[data-testid="span-annotation--label"]').text()).toBe('Start Time');
    // We cannot test timestamp because of timezone problems.
    expect(rows.childAt(1).find('[data-testid="span-annotation--label"]').text()).toBe('Relative Time');
    expect(rows.childAt(1).find('[data-testid="span-annotation--value"]').text()).toBe('700ms');
    expect(rows.childAt(2).find('[data-testid="span-annotation--label"]').text()).toBe('Address');
    expect(rows.childAt(2).find('[data-testid="span-annotation--value"]').text()).toBe('127.0.0.1');
  });
});
