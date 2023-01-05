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
import { Box, Button, makeStyles, Typography } from '@material-ui/core';
import { List as ListIcon } from '@material-ui/icons';
import React from 'react';
import AdjustedTrace from '../../../models/AdjustedTrace';
import Span from '../../../models/Span';
import { HeaderMenu } from './HeaderMenu';

const useStyles = makeStyles((theme) => ({
  root: {
    borderBottom: `1px solid ${theme.palette.divider}`,
  },
  titleRow: {
    backgroundColor: theme.palette.background.paper,
    padding: theme.spacing(1, 2),
    borderBottom: `1px solid ${theme.palette.divider}`,
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  titleRowRight: {
    display: 'flex',
    alignItems: 'center',
    '& > :not(:last-child)': {
      marginRight: theme.spacing(1),
    },
  },
  infoRow: {
    padding: theme.spacing(0.5, 2),
    display: 'flex',
    backgroundColor: theme.palette.grey[50],
    fontSize: theme.typography.body2.fontSize,
  },
  infoCell: {
    display: 'flex',
    fontWeight: theme.typography.fontWeightRegular,
    '&:not(:first-child)': {
      marginLeft: theme.spacing(1),
    },
    '&:not(:last-child)': {
      paddingRight: theme.spacing(1),
      borderRight: `1px solid ${theme.palette.divider}`,
    },
  },
  infoCellKey: {
    color: theme.palette.text.hint,
    marginRight: theme.spacing(0.5),
  },
  infoCellValue: {
    color: theme.palette.text.primary,
  },
}));

type HeaderProps = {
  trace: AdjustedTrace;
  rawTrace: Span[];
  toggleIsSpanTableOpen: () => void;
};

export const Header = ({
  trace,
  rawTrace,
  toggleIsSpanTableOpen,
}: HeaderProps) => {
  const classes = useStyles();
  const { i18n } = useLingui();

  return (
    <Box className={classes.root}>
      <Box className={classes.titleRow}>
        <Typography variant="h6">
          {`${trace.rootSpan.serviceName}: ${trace.rootSpan.spanName}`}
        </Typography>
        <Box className={classes.titleRowRight}>
          <Button
            variant="outlined"
            size="small"
            onClick={toggleIsSpanTableOpen}
            startIcon={<ListIcon />}
          >
            Span table
          </Button>
          <HeaderMenu trace={trace} rawTrace={rawTrace} />
        </Box>
      </Box>
      <Box className={classes.infoRow}>
        {[
          { key: i18n._(t`Duration`), value: trace.durationStr },
          {
            key: i18n._(t`Services`),
            value: trace.serviceNameAndSpanCounts.length,
          },
          { key: i18n._(t`Total Spans`), value: trace.spans.length },
          {
            key: i18n._(t`Trace ID`),
            value: `${trace.traceId}`,
          },
        ].map(({ key, value }) => (
          <Box key={key} className={classes.infoCell}>
            <Box className={classes.infoCellKey}>{key}</Box>
            <Box className={classes.infoCellValue}>{value}</Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
};
