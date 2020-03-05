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

import render from '../../test/util/render-with-default-settings';

import DiscoverPage from './DiscoverPage';

describe('<DiscoverPage />', () => {
  it('renders search box with default config', () => {
    const { getByText, queryByText } = render(<DiscoverPage />);
    expect(getByText('Search Traces')).toBeInTheDocument();
    expect(queryByText('Searching has been disabled via the searchEnabled property', { exact: false }))
      .not.toBeInTheDocument();
  });

  it('does not render search box with searchEnabled=false config', () => {
    const { getByText, queryByText } = render(<DiscoverPage />, {
      uiConfig: { searchEnabled: false },
    });
    expect(queryByText('Search Traces')).not.toBeInTheDocument();
    expect(getByText('Searching has been disabled via the searchEnabled property', { exact: false }))
      .toBeInTheDocument();
  });
});
