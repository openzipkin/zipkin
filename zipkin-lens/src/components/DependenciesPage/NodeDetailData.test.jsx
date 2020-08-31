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

import { fireEvent } from '@testing-library/react';
import { createMemoryHistory } from 'history';
import React from 'react';

import NodeDetailData from './NodeDetailData';
import render from '../../test/util/render-with-default-settings';

describe('<NodeDetailData />', () => {
  it('should go to the search traces page when the search traces button is clicked.', () => {
    const history = createMemoryHistory();

    const { getByTestId } = render(
      <NodeDetailData
        serviceName="serviceA"
        targetEdges={[]}
        sourceEdges={[]}
      />,
      { history },
    );

    fireEvent.click(getByTestId('search-traces-button'));

    expect(history.location.pathname).toBe('/');
    expect(history.location.search).toBe('?serviceName=serviceA');
  });
});
