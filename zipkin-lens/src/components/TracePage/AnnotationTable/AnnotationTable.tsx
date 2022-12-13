/*
 * Copyright 2015-2022 The OpenZipkin Authors
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

import { t } from '@lingui/macro';
import { useLingui } from '@lingui/react';
import {
  makeStyles,
  Table,
  TableBody,
  TableCell,
  TableRow,
} from '@material-ui/core';
import React from 'react';
import { AdjustedAnnotation } from '../../../models/AdjustedTrace';
import { formatTimestamp } from '../../../util/timestamp';

const useStyles = makeStyles((theme) => ({
  table: {
    tableLayout: 'fixed',
  },
  tableRow: {
    '&:last-child > *': {
      borderBottom: 'none',
    },
  },
  relativeTimeCell: {
    width: 120,
  },
  labelCell: {
    width: 120,
    color: theme.palette.text.secondary,
  },
  valueCell: {
    wordWrap: 'break-word',
  },
}));

type AnnotationTableProps = {
  annotations: AdjustedAnnotation[];
};

export const AnnotationTable = ({ annotations }: AnnotationTableProps) => {
  const classes = useStyles();
  const { i18n } = useLingui();

  return (
    <Table size="small" className={classes.table}>
      <TableBody>
        {annotations.map((annotation) => (
          <TableRow
            key={`${annotation.value}-${annotation.timestamp}`}
            className={classes.tableRow}
          >
            <TableCell className={classes.relativeTimeCell}>
              {annotation.relativeTime}
            </TableCell>
            <TableCell>
              <Table size="small" className={classes.table}>
                <TableBody>
                  {[
                    {
                      label: i18n._(t`Start Time`),
                      value: formatTimestamp(annotation.timestamp),
                    },
                    { label: 'Value', value: annotation.value },
                    { label: i18n._(t`Address`), value: annotation.endpoint },
                  ].map(({ label, value }) => (
                    <TableRow key={label} className={classes.tableRow}>
                      <TableCell className={classes.labelCell}>
                        {label}
                      </TableCell>
                      <TableCell className={classes.valueCell}>
                        {value}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
};
