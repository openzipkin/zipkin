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

import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@material-ui/core';
import React from 'react';

import TraceSummaryRow from './TraceSummaryRow';
import TraceSummary from '../../models/TraceSummary';

interface TraceSummaryTableProps {
  traceSummaries: TraceSummary[];
}

const TraceSummaryTable: React.FC<TraceSummaryTableProps> = ({
  traceSummaries,
}) => {
  return (
    <TableContainer>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Root Span</TableCell>
            <TableCell align="right">Start Time</TableCell>
            <TableCell align="right">Spans</TableCell>
            <TableCell align="right">Duration</TableCell>
            <TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {traceSummaries.map((traceSummary) => (
            <TraceSummaryRow traceSummary={traceSummary} />
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default TraceSummaryTable;
