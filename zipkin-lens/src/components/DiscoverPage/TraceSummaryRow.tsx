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

import {
  Box,
  IconButton,
  TableCell,
  TableRow,
  Typography,
  Collapse,
  Chip,
  makeStyles,
  createStyles,
  Theme,
} from '@material-ui/core';
import KeyboardArrowDownIcon from '@material-ui/icons/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@material-ui/icons/KeyboardArrowUp';
import moment from 'moment';
import React, { useState, useCallback } from 'react';
import styled from 'styled-components';

import { selectColorByInfoClass, selectServiceColor } from '../../colors';
import TraceSummary from '../../models/TraceSummary';
import { formatDuration } from '../../util/timestamp';

interface TraceSummaryRowProps {
  traceSummary: TraceSummary;
}

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    chipLabel: {
      ...theme.typography.body1,
    },
  }),
);

const TraceSummaryRow: React.FC<TraceSummaryRowProps> = ({ traceSummary }) => {
  const classes = useStyles();
  const [open, setOpen] = useState(false);
  const handleOpenButtonClick = useCallback(() => {
    setOpen((prev) => !prev);
  }, []);
  const startTime = moment(traceSummary.timestamp / 1000);

  return (
    <>
      <Root>
        <TableCell>
          <IconButton size="small" onClick={handleOpenButtonClick}>
            {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell>
          <Box display="flex" alignItems="center">
            <ServiceNameChip
              size="small"
              serviceName={traceSummary.root.serviceName}
              label={traceSummary.root.serviceName}
              classes={{ label: classes.chipLabel }}
            />
            <Typography variant="body1" color="textSecondary">
              {traceSummary.root.spanName}
            </Typography>
          </Box>
        </TableCell>
        <TableCell>
          <Box display="flex" justifyContent="flex-end" alignItems="center">
            <FromNowTypography>{startTime.fromNow()}</FromNowTypography>
            <Typography variant="body2" color="textSecondary">
              ({startTime.format('MM/DD HH:mm:ss:SSS')})
            </Typography>
          </Box>
        </TableCell>
        <TableCell align="right">{traceSummary.spanCount}</TableCell>
        <TableCell align="right">
          <Box position="relative" width="100%">
            {formatDuration(traceSummary.duration)}
            <DurationBar
              width={traceSummary.width}
              infoClass={traceSummary.infoClass}
            />
          </Box>
        </TableCell>
      </Root>
      <TableRow>
        <CollapsibleTableCell>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box margin={1}>
              <Box display="flex" alignItems="center">
                <Typography variant="caption" color="textSecondary">
                  Trace ID:
                </Typography>
                <TraceIdTypography variant="caption">
                  {traceSummary.traceId}
                </TraceIdTypography>
              </Box>
            </Box>
          </Collapse>
        </CollapsibleTableCell>
      </TableRow>
    </>
  );
};

export default TraceSummaryRow;

const Root = styled(TableRow)`
  & > * {
    border-bottom: unset;
  }
`;

const ServiceNameChip = styled(Chip).attrs<{ serviceName: string }>({
  color: 'primary',
})<{ serviceName: string }>`
  background-color: ${({ serviceName }) => selectServiceColor(serviceName)};
  margin-right: ${({ theme }) => theme.spacing(1)}px;
`;

const FromNowTypography = styled(Typography).attrs({
  variant: 'body2',
})`
  margin-right: ${({ theme }) => theme.spacing(1)}px;
`;

const DurationBar = styled.div<{ width: number; infoClass?: string }>`
  position: absolute;
  background-color: ${({ infoClass }) =>
    selectColorByInfoClass(infoClass || '')};
  opacity: 0.3;
  top: 0;
  left: 0;
  height: 100%;
  width: ${({ width }) => width}%;
  border-top-right-radius: 3px;
  border-bottom-right-radius: 3px;
`;

const CollapsibleTableCell = styled(TableCell).attrs({
  colSpan: 6,
})`
  padding-bottom: 0;
  padding-top: 0;
`;

const TraceIdTypography = styled(Typography).attrs({
  variant: 'caption',
})`
  margin-left: ${({ theme }) => theme.spacing(0.5)}px;
`;
