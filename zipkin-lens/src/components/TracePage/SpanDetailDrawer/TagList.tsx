/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  Box,
  Collapse,
  IconButton,
  makeStyles,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Typography,
} from '@material-ui/core';
import {
  KeyboardArrowDown as KeyboardArrowDownIcon,
  KeyboardArrowUp as KeyboardArrowUpIcon,
} from '@material-ui/icons';
import React from 'react';
import { useToggle } from 'react-use';
import { AdjustedSpan } from '../../../models/AdjustedTrace';

const useStyles = makeStyles((theme) => ({
  table: {
    tableLayout: 'fixed',
  },
  tableRow: {
    '&:first-child > *': {
      borderTop: `1px solid ${theme.palette.divider}`,
    },
  },
  labelCell: {
    color: theme.palette.text.secondary,
    wordWrap: 'break-word',
  },
  valueCell: {
    wordWrap: 'break-word',
  },
}));

type TagListProps = {
  span: AdjustedSpan;
};

export const TagList = ({ span }: TagListProps) => {
  const classes = useStyles();
  const [open, toggleOpen] = useToggle(true);

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Typography>Tags</Typography>
        <IconButton onClick={toggleOpen} size="small">
          {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
        </IconButton>
      </Box>
      <Collapse in={open}>
        <Box mt={1.5}>
          <Table size="small" className={classes.table}>
            <TableBody>
              {span.tags.map((tag) => (
                <TableRow key={tag.key} className={classes.tableRow}>
                  <TableCell className={classes.labelCell}>{tag.key}</TableCell>
                  <TableCell className={classes.valueCell}>
                    {tag.value}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      </Collapse>
    </Box>
  );
};
