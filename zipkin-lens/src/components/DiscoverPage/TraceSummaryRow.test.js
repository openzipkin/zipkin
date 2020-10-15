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

import TraceSummaryRow from './TraceSummaryRow';
import render from '../../test/util/render-with-default-settings';

describe('<TraceSummaryRow />', () => {
  it('should render timestamp and duration in correct unit', () => {
    const { queryByTestId } = render(
      <table>
        <tbody>
          <TraceSummaryRow
            traceSummary={{
              traceId: 'a03ee8fff1dcd9b9',
              timestamp: 1571896375237354,
              duration: 131848,
              serviceSummaries: [],
              spanCount: 10,
              width: 10,
              root: {
                serviceName: 'routing',
                spanName: 'post /location/update/v4',
              },
            }}
          />
        </tbody>
      </table>,
    );

    const startTimeFormat = queryByTestId('TraceSummaryRow-startTimeFormat');
    expect(startTimeFormat).toBeInTheDocument();
    // Don't assert on hour as the timezone will be different in CI
    expect(startTimeFormat).toHaveTextContent(/10\/2[34] [0-9][0-9]:52:55:237/);
    // Intentionally not asserting the relative time from now as it would drift tests

    const duration = queryByTestId('TraceSummaryRow-duration');
    expect(duration).toBeInTheDocument();
    expect(duration).toHaveTextContent('131.848ms');
  });
});
