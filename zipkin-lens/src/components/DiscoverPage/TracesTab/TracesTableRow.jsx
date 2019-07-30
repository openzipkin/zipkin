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
import React, { useCallback } from 'react';
import { connect } from 'react-redux';
import { withRouter } from 'react-router';
import moment from 'moment';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Grid from '@material-ui/core/Grid';
import grey from '@material-ui/core/colors/grey';
import { fade } from '@material-ui/core/styles/colorManipulator';

import ServiceBadge from '../../Common/ServiceBadge';
import { rootServiceAndSpanName } from '../../../zipkin';
import { traceSummaryPropTypes } from '../../../prop-types';
import { theme } from '../../../colors';

const propTypes = {
  traceSummary: traceSummaryPropTypes.isRequired,
  onAddFilter: PropTypes.func.isRequired,
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  correctedTraceMap: PropTypes.shape({}).isRequired,
};

const useStyles = makeStyles({
  root: {
    cursor: 'pointer',
    '&:hover': {
      backgroundColor: grey[100],
    },
  },
  data: {
    position: 'relative',
    borderBottom: `1px solid ${grey[200]}`,
  },
  durationBar: {
    backgroundColor: fade(theme.palette.primary.light, 0.4),
  },
  dataCell: {
    display: 'flex',
    paddingLeft: '1rem',
    paddingRight: '1rem',
    paddingTop: '1.2rem',
    paddingBottom: '1.2rem',
  },
  badgeRow: {
    paddingLeft: '1rem',
    paddingRight: '1rem',
    paddingTop: '0.6rem',
    paddingBottom: '0.6rem',
    borderBottom: `1px solid ${grey[300]}`,
  },
  serviceName: {
    textTrasform: 'uppercase',
  },
  subInfo: {
    marginLeft: '0.6rem',
    color: theme.palette.text.hint,
  },
});

export const TracesTableRowImpl = ({
  traceSummary,
  onAddFilter,
  history,
  correctedTraceMap,
}) => {
  const classes = useStyles();

  const startTime = moment(traceSummary.timestamp / 1000);

  const correctedTrace = correctedTraceMap[traceSummary.traceId];
  const { spanName, serviceName } = rootServiceAndSpanName(correctedTrace);

  const handleClick = useCallback(() => {
    history.push(`/zipkin/traces/${traceSummary.traceId}`);
  }, [history, traceSummary.traceId]);

  return (
    <Box className={classes.root} onClick={handleClick} data-test="root">
      <Grid container spacing={0} className={classes.data}>
        <Box
          position="absolute"
          width={`${traceSummary.width}%`}
          height="100%"
          className={classes.durationBar}
          data-test="duration-bar"
        />
        <Grid item xs={3} className={classes.dataCell}>
          <Box className={classes.serviceName} data-test="service-name">
            {`${serviceName}`}
          </Box>
          <Box className={classes.subInfo} data-test="span-name">
            {`(${spanName})`}
          </Box>
        </Grid>
        <Grid item xs={3} className={classes.dataCell}>
          {traceSummary.traceId}
        </Grid>
        <Grid item xs={3} className={classes.dataCell}>
          <Box>
            {startTime.format('MM/DD HH:mm:ss:SSS')}
          </Box>
          <Box className={classes.subInfo}>
            {`(${startTime.fromNow()})`}
          </Box>
        </Grid>
        <Grid item xs={3} className={classes.dataCell}>
          {traceSummary.durationStr}
        </Grid>
      </Grid>
      <Box display="flex" flexWrap="wrap" className={classes.badgeRow}>
        {
          traceSummary.serviceSummaries.map(serviceSummary => (
            <Box key={serviceSummary.serviceName} mr={0.2} ml={0.2}>
              <ServiceBadge
                serviceName={serviceSummary.serviceName}
                count={serviceSummary.spanCount}
                onClick={(event) => {
                  onAddFilter(serviceSummary.serviceName);
                  event.stopPropagation();
                }}
              />
            </Box>
          ))
        }
      </Box>
    </Box>
  );
};

TracesTableRowImpl.propTypes = propTypes;

const mapStateToProps = state => ({
  correctedTraceMap: state.traces.correctedTraceMap,
});

export default connect(
  mapStateToProps,
  null,
)(withRouter(TracesTableRowImpl));
