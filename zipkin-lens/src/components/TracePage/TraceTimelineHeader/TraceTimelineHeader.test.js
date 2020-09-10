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
import { createMount } from '@material-ui/core/test-utils';
import { ThemeProvider } from '@material-ui/styles';

import TraceTimelineHeader from './TraceTimelineHeader';
import { theme } from '../../../constants/color';

// Only display the component because there is nothing to be tested.
it('<TraceTimelineHeader />', () => {
  const mount = createMount();
  mount(
    <ThemeProvider theme={theme}>
      <TraceTimelineHeader
        startTs={10}
        endTs={100}
        isRerooted
        isRootedTrace
        onResetRerootButtonClick={() => {}}
        isSpanDetailOpened
        onSpanDetailToggle={() => {}}
        onCollapseButtonClick={() => {}}
        onExpandButtonClick={() => {}}
        classes={{}}
      />
    </ThemeProvider>,
  );
});
