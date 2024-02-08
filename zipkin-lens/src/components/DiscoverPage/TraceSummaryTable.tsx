/*
 * Copyright 2015-2023 The OpenZipkin Authors
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
  TableHead,
  TableRow,
  TableSortLabel,
} from '@material-ui/core';
import React, { useState, useCallback } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import TraceSummary from '../../models/TraceSummary';
import TraceSummaryRow from './TraceSummaryRow';

interface TraceSummaryTableProps {
  traceSummaries: TraceSummary[];
  toggleFilter: (serivceName: string) => void;
  traceSummaryOpenMap: { [key: string]: boolean };
  toggleTraceSummaryOpen: (traceId: string) => void;
}

const TraceSummaryTable: React.FC<TraceSummaryTableProps> = ({
  traceSummaries,
  toggleFilter,
  traceSummaryOpenMap,
  toggleTraceSummaryOpen,
}) => {
  const [order, setOrder] = useState<'asc' | 'desc'>('desc');
  const [orderBy, setOrderBy] = useState<
    'duration' | 'timestamp' | 'spanCount'
  >('duration');
  const { t } = useTranslation();

  const handleSortButtonClick = useCallback(
    (event: React.MouseEvent<HTMLDivElement>) => {
      const { cellkey } = event.currentTarget.dataset;
      if (
        !cellkey ||
        (cellkey !== 'duration' &&
          cellkey !== 'timestamp' &&
          cellkey !== 'spanCount')
      ) {
        return;
      }

      if (cellkey === orderBy) {
        setOrder((prev) => {
          if (prev === 'asc') return 'desc';
          return 'asc';
        });
      } else {
        setOrder('desc');
        setOrderBy(cellkey);
      }
    },
    [orderBy],
  );

  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell />
          <TableCell>
            <Trans t={t}>Root</Trans>
          </TableCell>
          {[
            { label: <Trans t={t}>Start Time</Trans>, key: 'timestamp' },
            { label: <Trans t={t}>Spans</Trans>, key: 'spanCount' },
            { label: <Trans t={t}>Duration</Trans>, key: 'duration' },
          ].map(({ label, key }) => (
            <TableCell
              key={key}
              align="right"
              sortDirection={orderBy === key ? order : false}
            >
              <TableSortLabel
                active={orderBy === key}
                direction={orderBy === key ? order : 'asc'}
                data-cellkey={key}
                onClick={handleSortButtonClick}
              >
                {label}
              </TableSortLabel>
            </TableCell>
          ))}
          <TableCell />
        </TableRow>
      </TableHead>
      <TableBody>
        {traceSummaries
          .sort((a, b) => {
            if (order === 'asc') {
              return a[orderBy] - b[orderBy];
            }
            return b[orderBy] - a[orderBy];
          })
          .map((traceSummary) => (
            <TraceSummaryRow
              key={traceSummary.traceId}
              traceSummary={traceSummary}
              toggleFilter={toggleFilter}
              open={!!traceSummaryOpenMap[traceSummary.traceId]}
              toggleOpen={toggleTraceSummaryOpen}
            />
          ))}
      </TableBody>
    </Table>
  );
};

export default TraceSummaryTable;
