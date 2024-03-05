/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
