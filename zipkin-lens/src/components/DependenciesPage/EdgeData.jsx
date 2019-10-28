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
import PropTypes from 'prop-types';
import React from 'react';
import Box from '@material-ui/core/Box';
import Paper from '@material-ui/core/Paper';
import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableRow from '@material-ui/core/TableRow';
import TableCell from '@material-ui/core/TableCell';
import { makeStyles } from '@material-ui/styles';
import ServiceBadge from '../Common/ServiceBadge';

const useStyles = makeStyles(theme => ({
  root: {
    marginTop: theme.spacing(0.75),
  },
  cell: {
    paddingTop: theme.spacing(1),
    paddingBottom: theme.spacing(1),
  },
}));

const propTypes = {
  nodeName: PropTypes.string.isRequired,
  normalCount: PropTypes.number.isRequired,
  errorCount: PropTypes.number.isRequired,
};

const EdgeData = React.memo(({
  nodeName,
  normalCount,
  errorCount,
}) => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      <ServiceBadge serviceName={nodeName} />
      <Paper>
        <Table>
          <TableBody>
            <TableRow>
              <TableCell className={classes.cell}>
                NORMAL
              </TableCell>
              <TableCell className={classes.cell}>
                {normalCount}
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell className={classes.cell}>
                ERROR
              </TableCell>
              <TableCell className={classes.cell}>
                {errorCount}
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </Paper>
    </Box>
  );
});

EdgeData.propTypes = propTypes;

export default EdgeData;
