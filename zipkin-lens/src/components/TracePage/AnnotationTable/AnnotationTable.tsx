/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  makeStyles,
  Table,
  TableBody,
  TableCell,
  TableRow,
} from '@material-ui/core';
import React from 'react';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();

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
                      label: t(`Start Time`),
                      value: formatTimestamp(annotation.timestamp),
                    },
                    { label: 'Value', value: annotation.value },
                    { label: t(`Address`), value: annotation.endpoint },
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
