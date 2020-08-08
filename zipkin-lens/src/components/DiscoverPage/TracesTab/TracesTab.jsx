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
import { Trans } from '@lingui/macro';
import React, { useState, useMemo, useCallback } from 'react';
import { connect } from 'react-redux';
import Box from '@material-ui/core/Box';
import { makeStyles } from '@material-ui/styles';

import {
  sortingMethods,
  sortTraceSummaries,
  extractAllServiceNames,
  filterTraceSummaries,
} from './util';
import TracesTable from './TracesTable';
import ServiceFilter from './ServiceFilter';
import { traceSummariesPropTypes } from '../../../prop-types';

const propTypes = {
  traceSummaries: traceSummariesPropTypes.isRequired,
};

const useStyles = makeStyles((theme) => ({
  header: {
    borderColor: theme.palette.grey[300],
  },
}));

export const TracesTab = ({ traceSummaries }) => {
  // Export for testing.
  const classes = useStyles();
  const allServiceNames = useMemo(
    () => extractAllServiceNames(traceSummaries),
    [traceSummaries],
  );
  const [sortingMethod, setSortingMethod] = useState(
    sortingMethods.LONGEST_FIRST,
  );

  const handleSortingMethodChange = useCallback((newSortingMethod) => {
    setSortingMethod(newSortingMethod);
  }, []);

  const [filters, setFilters] = useState([]);

  const handleAddFilter = useCallback(
    (filter) => {
      if (!filters.includes(filter)) {
        setFilters([...filters, filter]);
      }
    },
    [filters],
  );

  const handleDeleteFilter = useCallback(
    (filter) => {
      setFilters(filters.filter((f) => f !== filter));
    },
    [filters],
  );

  const filteredTraceSummaries = useMemo(
    () =>
      sortTraceSummaries(
        filterTraceSummaries(traceSummaries, filters),
        sortingMethod,
      ),
    [filters, traceSummaries, sortingMethod],
  );

  return (
    <Box display="flex" flexDirection="column">
      <Box
        borderBottom={1}
        display="flex"
        justifyContent="space-between"
        p={1}
        className={classes.header}
      >
        <Box
          display="flex"
          alignItems="center"
          fontSize="1.05rem"
          data-test="count-results"
        >
          {
            // DiscoverPage.jsx renders an explain screen when there are no traces. The only
            // options here are one or more traces matching the search criteria.
            traceSummaries.length === 1 ? (
              <Trans>One Result</Trans>
            ) : (
              <Trans>{traceSummaries.length} Results</Trans>
            )
          }
        </Box>
        <Box mr={2}>
          <ServiceFilter
            filters={filters}
            onAddFilter={handleAddFilter}
            onDeleteFilter={handleDeleteFilter}
            allServiceNames={allServiceNames}
          />
        </Box>
      </Box>
      <TracesTable
        sortingMethod={sortingMethod}
        traceSummaries={filteredTraceSummaries}
        onAddFilter={handleAddFilter}
        onSortingMethodChange={handleSortingMethodChange}
      />
    </Box>
  );
};

TracesTab.propTypes = propTypes;

const mapStateToProps = (state) => ({
  traceSummaries: state.traces.traceSummaries,
});

// React-Redux already provides two hooks API, useSelector and useDispatch.
// However, I think that using them would make tests more complex, so for now
// I decided to keep using connect API.
export default connect(mapStateToProps, null)(TracesTab);
