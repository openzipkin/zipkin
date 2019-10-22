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
import { withStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableRow from '@material-ui/core/TableRow';
import Paper from '@material-ui/core/Paper';

import { spanAnnotationPropTypes } from '../../../prop-types';
import { formatTimestampMicros } from '../../../util/timestamp';

const propTypes = {
  annotation: spanAnnotationPropTypes.isRequired,
  classes: PropTypes.shape({}).isRequired,
};

const style = theme => ({
  cell: {
    paddingTop: theme.spacing(1),
    paddingBottom: theme.spacing(1),
  },
});

const SpanAnnotation = React.memo(({ annotation, classes }) => (
  <Box>
    <Box fontSize="1.1rem" mb={0.5}>
      {annotation.value}
    </Box>
    <Paper>
      <Table>
        <TableBody data-testid="span-annotation--table-body">
          {
            [
              { label: 'Start Time', value: formatTimestampMicros(annotation.timestamp) },
              { label: 'Relative Time', value: annotation.relativeTime },
              { label: 'Address', value: annotation.endpoint },
            ].map(e => (
              <TableRow key={e.label}>
                <TableCell className={classes.cell} data-testid="span-annotation--label">
                  {e.label}
                </TableCell>
                <TableCell className={classes.cell} data-testid="span-annotation--value">
                  {e.value}
                </TableCell>
              </TableRow>
            ))
          }
        </TableBody>
      </Table>
    </Paper>
  </Box>
));

SpanAnnotation.propTypes = propTypes;

export default withStyles(style)(SpanAnnotation);
