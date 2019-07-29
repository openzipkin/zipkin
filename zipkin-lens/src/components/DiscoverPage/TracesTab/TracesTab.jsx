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

import { sortingMethods, sortTraceSummaries } from './sorting';
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
    return Array.from(new Set(result)); // For uniqueness
  }, [traceSummaries]);

  const [sortingMethod, setSortingMethod] = useState(sortingMethods.LONGEST_FIRST);

  const handleSortingMethodChange = useCallback((newSortingMethod) => {
    setSortingMethod(newSortingMethod);
  }, []);

  const [filters, setFilters] = useState([]);

  const handleAddFilter = useCallback((filter) => {
    if (!filters.includes(filter)) {
      setFilters([
        ...filters,
        filter,
      ]);
    }
  }, [filters]);

  const handleDeleteFilter = useCallback((filter) => {
    setFilters(filters.filter(f => f !== filter));
  }, [filters]);

  const filteredTraceSummaries = useMemo(() => sortTraceSummaries(
    traceSummaries.filter((traceSummary) => {
      for (let i = 0; i < filters.length; i += 1) {
        if (!traceSummary.serviceSummaries.find(
          serviceSummary => serviceSummary.serviceName === filters[i],
        )) {
          return false;
        }
      }
      return true;
    }),
    sortingMethod,
  ), [filters, traceSummaries, sortingMethod]);

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

export default TracesTab;
