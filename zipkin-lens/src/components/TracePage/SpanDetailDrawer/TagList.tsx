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
