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
import { AutoSizer } from 'react-virtualized';
import Box from '@material-ui/core/Box';

import TracesTableHead from './TracesTableHead';
import TracesTableBody from './TracesTableBody';

const TracesTable = () => (
  <React.Fragment>
    <TracesTableHead />
    <Box height="100%">
      <AutoSizer>
        {
          ({ height, width }) => (
            <Box width={width} height={height} overflow="auto">
              <TracesTableBody />
            </Box>
          )
        }
      </AutoSizer>
    </Box>
  </React.Fragment>
);

export default TracesTable;
