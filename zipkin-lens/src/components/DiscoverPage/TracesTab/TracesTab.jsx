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
import Button from '@material-ui/core/Button';

import ServiceFilter from './ServiceFilter';

import { treeCorrectedForClockSkew, traceSummary as buildTraceSummary, traceSummaries as buildTraceSummaries } from '../../../zipkin';

const TracesTab = () => {
  const traces = useSelector(state => state.traces.traces);

  const correctedTraces = useMemo(() => traces.map(treeCorrectedForClockSkew), [traces]);

  const traceSummaries = useMemo(() => buildTraceSummaries(
    null,
    correctedTraces.map(buildTraceSummary),
  ), [correctedTraces]);

  const tracesMap = useMemo(() => {
    const result = {};
    correctedTraces.forEach((trace, index) => {
      const [{ traceId }] = traces[index];
      result[traceId] = trace;
    });
  }, [correctedTraces, traces]);

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
        <ServiceFilter
          filters={filters}
          addFilter={addFilter}
          deleteFilter={deleteFilter}
          allServiceNames={allServiceNames}
        />
      </Box>
      <Box width="100%" height="100%">
        <div>
          TODO: TracesTable
        </div>
      </Box>
    </Box>
  );
};

export default TracesTab;
