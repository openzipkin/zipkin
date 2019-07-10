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
import React from 'react';
import { makeStyles } from '@material-ui/styles';
import TableHead from '@material-ui/core/TableHead';
import TableRow from '@material-ui/core/TableRow';
import TableCell from '@material-ui/core/TableCell';
import grey from '@material-ui/core/colors/grey';

const useStyles = makeStyles({
  root: {
    backgroundColor: grey[100],
    textTransform: 'uppercase',
  },
});

const TracesTableHead = () => {
  const classes = useStyles();

  return (
    <TableHead className={classes.root}>
      <TableRow>
        <TableCell>Trace ID</TableCell>
        <TableCell>Start Time</TableCell>
        <TableCell>Duration</TableCell>
      </TableRow>
    </TableHead>
  );
};

export default TracesTableHead;
