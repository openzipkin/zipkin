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

import SpanTags from './SpanTags';
import render from '../../../test/util/render-with-default-settings';

describe('<SpanTags />', () => {
  it('should render all tags', () => {
    const { queryAllByTestId } = render(
      <SpanTags
        tags={[
          { key: 'key1', value: 'value1' },
          { key: 'key2', value: 'value2' },
          { key: 'key3', value: 'value3' },
          { key: 'key4', value: 'value4\nvalue4' },
        ]}
      />,
    );
    const keys = queryAllByTestId('SpanTags-key');
    const values = queryAllByTestId('SpanTags-value');
    expect(keys[0]).toHaveTextContent('key1');
    expect(values[0]).toHaveTextContent('value1');
    expect(keys[1]).toHaveTextContent('key2');
    expect(values[1]).toHaveTextContent('value2');
    expect(keys[2]).toHaveTextContent('key3');
    expect(values[2]).toHaveTextContent('value3');
    expect(keys[3]).toHaveTextContent('key4');
    expect(values[3]).toHaveTextContent('value4 value4');
  });
});
