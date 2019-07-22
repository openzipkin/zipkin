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
import React, { useState, useMemo, useCallback } from 'react';
import { useSelector } from 'react-redux';
import Box from '@material-ui/core/Box';

import TracesTable from './TracesTable';
import ServiceFilter from './ServiceFilter';

const TracesTab = () => {
  const traceSummaries = useSelector(state => state.traces.traceSummaries);

  const allServiceNames = useMemo(() => {
    const result = [];
    traceSummaries.forEach((traceSummary) => {
      if (!traceSummary.serviceSummaries) {
        return;
      }
      traceSummary.serviceSummaries.forEach((serviceSummary) => {
        result.push(serviceSummary.serviceName);
      });
    });
    return result;
  }, [traceSummaries]);

  const [filters, setFilters] = useState([]);

  const addFilter = useCallback((filter) => {
    setFilters([
      ...filters,
      filter,
    ]);
  }, [filters]);

  const deleteFilter = useCallback((filter) => {
    setFilters(filters.filter(f => f !== filter));
  }, [filters]);

  return (
    <Box height="100%" display="flex" flexDirection="column">
      <Box borderBottom={1} borderColor="grey.300" display="flex" justifyContent="space-between" p={1}>
        <Box display="flex" alignItems="center" fontSize="1.05rem">
          {traceSummaries.length}
          &nbsp;
          Results
        </Box>
        <Box mr={2}>
          <ServiceFilter
            filters={filters}
            addFilter={addFilter}
            deleteFilter={deleteFilter}
            allServiceNames={allServiceNames}
          />
        </Box>
      </Box>
      <TracesTable />
    </Box>
  );
};

export default TracesTab;
