/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import React from 'react';
import { makeStyles } from '@material-ui/styles';
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
};

const useStyles = makeStyles((theme) => ({
  cell: {
    paddingTop: theme.spacing(1),
    paddingBottom: theme.spacing(1),
    // overflow-wrap does not work well for elements with "display: table-cell",
    // so use word-break as a workaround.
    wordBreak: 'break-all',
  },
}));

const SpanAnnotation = React.memo(({ annotation }) => {
  const classes = useStyles();
  const { i18n } = useLingui();

  return (
    <Box>
      <Box fontSize="1.1rem" mb={0.5}>
        {annotation.value}
      </Box>
      <Paper>
        <Table>
          <TableBody data-testid="span-annotation--table-body">
            {[
              {
                label: i18n._(t`Start Time`),
                value: formatTimestampMicros(annotation.timestamp),
              },
              {
                label: i18n._(t`Relative Time`),
                value: annotation.relativeTime,
              },
              { label: i18n._(t`Address`), value: annotation.endpoint },
            ].map((e) => (
              <TableRow key={e.label}>
                <TableCell
                  className={classes.cell}
                  data-testid="span-annotation--label"
                >
                  {e.label}
                </TableCell>
                <TableCell
                  className={classes.cell}
                  data-testid="span-annotation--value"
                >
                  {e.value}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Box>
  );
});

SpanAnnotation.propTypes = propTypes;

export default SpanAnnotation;
