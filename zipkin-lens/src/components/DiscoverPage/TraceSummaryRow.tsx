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
  Collapse,
  IconButton,
  TableCell,
  TableRow,
  Typography,
  Button,
} from '@material-ui/core';
import KeyboardArrowDownIcon from '@material-ui/icons/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@material-ui/icons/KeyboardArrowUp';
import moment from 'moment';
import React, { useMemo, useCallback } from 'react';
import { Link } from 'react-router-dom';
import styled from 'styled-components';
import { selectColorByInfoClass } from '../../constants/color';
import TraceSummary from '../../models/TraceSummary';
import { formatDuration } from '../../util/timestamp';
import ServiceBadge from '../common/ServiceBadge';

interface TraceSummaryRowProps {
  traceSummary: TraceSummary;
  toggleFilter: (serviceName: string) => void;
  open: boolean;
  toggleOpen: (traceId: string) => void;
}

const TraceSummaryRow: React.FC<TraceSummaryRowProps> = ({
  traceSummary,
  toggleFilter,
  open,
  toggleOpen,
}) => {
  const startTime = moment(traceSummary.timestamp / 1000);

  const sortedServiceSummaries = useMemo(
    () =>
      [...traceSummary.serviceSummaries].sort(
        (a, b) => b.spanCount - a.spanCount,
      ),
    [traceSummary.serviceSummaries],
  );

  const handleToggleOpen = useCallback(() => {
    toggleOpen(traceSummary.traceId);
  }, [toggleOpen, traceSummary.traceId]);

  return (
    <>
      <Root>
        <TableCell>
          <IconButton size="small" onClick={handleToggleOpen}>
            {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell>
          <Box display="flex" alignItems="center">
            {`${traceSummary.root.serviceName}: ${traceSummary.root.spanName}`}
          </Box>
        </TableCell>
        <TableCell>
          <Box display="flex" justifyContent="flex-end" alignItems="center">
            <FromNowTypography>{startTime.fromNow()}</FromNowTypography>
            <Typography
              variant="body2"
              color="textSecondary"
              data-testid="TraceSummaryRow-startTimeFormat"
            >
              ({startTime.format('MM/DD HH:mm:ss:SSS')})
            </Typography>
          </Box>
        </TableCell>
        <TableCell align="right">{traceSummary.spanCount}</TableCell>
        <TableCell align="right">
          <Box
            position="relative"
            width="100%"
            data-testid="TraceSummaryRow-duration"
          >
            {formatDuration(traceSummary.duration)}
            <DurationBar
              width={traceSummary.width}
              infoClass={traceSummary.infoClass}
            />
          </Box>
        </TableCell>
        <TableCell>
          <Button
            variant="outlined"
            size="small"
            component={Link}
            to={`traces/${traceSummary.traceId}`}
          >
            Show
          </Button>
        </TableCell>
      </Root>
      <TableRow>
        <CollapsibleTableCell>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box margin={1}>
              <BadgesWrapper>
                {sortedServiceSummaries.map((serviceSummary) => (
                  <ServiceBadge
                    key={serviceSummary.serviceName}
                    serviceName={serviceSummary.serviceName}
                    count={serviceSummary.spanCount}
                    onClick={toggleFilter}
                  />
                ))}
              </BadgesWrapper>
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

const BadgesWrapper = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.spacing(0.5)}px;
`;
