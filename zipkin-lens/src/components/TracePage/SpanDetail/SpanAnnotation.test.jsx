/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import SpanAnnotation from './SpanAnnotation';

import render from '../../../test/util/render-with-default-settings';

describe('<SpanAnnotation />', () => {
  it('should render annotation data', () => {
    const { queryAllByTestId } = render(
      <SpanAnnotation
        annotation={{
          value: 'Server Start',
          timestamp: 1543334627716006,
          relativeTime: '700ms',
          endpoint: '127.0.0.1',
        }}
      />,
    );
    const labels = queryAllByTestId('span-annotation--label');
    const values = queryAllByTestId('span-annotation--value');
    expect(labels[0]).toHaveTextContent('Start Time');
    // We cannot test timestamp because of timezone problems.
    expect(labels[1]).toHaveTextContent('Relative Time');
    expect(values[1]).toHaveTextContent('700ms');
    expect(labels[2]).toHaveTextContent('Address');
    expect(values[2]).toHaveTextContent('127.0.0.1');
  });
});
