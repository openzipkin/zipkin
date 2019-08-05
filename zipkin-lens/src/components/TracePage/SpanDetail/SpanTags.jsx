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
import Box from '@material-ui/core/Box';
import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableRow from '@material-ui/core/TableRow';
import Paper from '@material-ui/core/Paper';
import grey from '@material-ui/core/colors/grey';
import { spanTagsPropTypes } from '../../../prop-types';

const useStyles = makeStyles({
  cell: {
    paddingTop: '8px',
    paddingBottom: '8px',
  },
  key: {
    color: grey[500],
    fontWeight: 'bold',
  },
  value: {
    fontSize: '1.05rem',
  },
});

const propTypes = {
  tags: spanTagsPropTypes.isRequired,
};

const SpanTags = ({ tags }) => {
  const classes = useStyles();

  return (
    <Paper>
      <Table>
        <TableBody>
          {
            tags.map(tag => (
              <TableRow key={tag.key}>
                <TableCell className={classes.cell}>
                  <Box className={classes.key}>
                    {tag.key}
                  </Box>
                  <Box className={classes.value}>
                    {tag.value}
                  </Box>
                </TableCell>
              </TableRow>
            ))
          }
        </TableBody>
      </Table>
    </Paper>
  );
};

SpanTags.propTypes = propTypes;

export default SpanTags;
