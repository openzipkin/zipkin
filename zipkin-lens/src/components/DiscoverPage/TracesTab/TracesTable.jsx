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
import PropTypes from 'prop-types';
import React from 'react';
import { AutoSizer } from 'react-virtualized';
import Box from '@material-ui/core/Box';

import TracesTableHead from './TracesTableHead';
import TracesTableRow from './TracesTableRow';
import { traceSummariesPropTypes } from '../../../prop-types';

const propTypes = {
  sortingMethod: PropTypes.string.isRequired,
  traceSummaries: traceSummariesPropTypes.isRequired,
  onAddFilter: PropTypes.func.isRequired,
  onSortingMethodChange: PropTypes.func.isRequired,
};

const TracesTable = ({
  sortingMethod,
  traceSummaries,
  onAddFilter,
  onSortingMethodChange,
}) => (
  <>
    <TracesTableHead
      sortingMethod={sortingMethod}
      onSortingMethodChange={onSortingMethodChange}
    />
    <Box height="100%">
      <AutoSizer>
        {
          ({ height, width }) => (
            <Box width={width} height={height} overflow="auto">
              {
                traceSummaries.map(traceSummary => (
                  <TracesTableRow
                    key={traceSummary.traceId}
                    traceSummary={traceSummary}
                    onAddFilter={onAddFilter}
                  />
                ))
              }
            </Box>
          )
        }
      </AutoSizer>
    </Box>
  </>
);

TracesTable.propTypes = propTypes;

export default TracesTable;
