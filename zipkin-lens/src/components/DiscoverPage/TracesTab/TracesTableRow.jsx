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
import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import { Link, withRouter } from 'react-router-dom';
import moment from 'moment';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Grid from '@material-ui/core/Grid';

import ServiceBadge from '../../Common/ServiceBadge';
import { getServiceName } from '../../../zipkin';
import { traceSummaryPropTypes } from '../../../prop-types';
import { selectColorByInfoClass } from '../../../colors';

export function rootServiceAndSpanName(root) {
  const { span } = root;
  if (span) {
    const serviceName =
      getServiceName(span.localEndpoint) || getServiceName(span.remoteEndpoint);
    return {
      serviceName: serviceName || 'unknown',
      spanName: span.name || 'unknown',
    };
  }
  return {
    serviceName: 'unknown',
    spanName: 'unknown',
  };
}

const propTypes = {
  traceSummary: traceSummaryPropTypes.isRequired,
  onAddFilter: PropTypes.func.isRequired,
  correctedTraceMap: PropTypes.shape({}).isRequired,
};

const useStyles = makeStyles((theme) => ({
  root: {
    cursor: 'pointer',
    '&:hover': {
      backgroundColor: theme.palette.grey[100],
    },
  },
  anchor: {
    color: theme.palette.text.primary,
    textDecoration: 'none',
    outline: 'none',
  },
  data: {
    position: 'relative',
    borderBottom: `1px solid ${theme.palette.grey[200]}`,
  },
  durationBar: {
    opacity: 0.4,
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
    borderBottom: `1px solid ${theme.palette.grey[300]}`,
  },
  serviceName: {
    textTransform: 'uppercase',
  },
  subInfo: {
    marginLeft: '0.6rem',
    color: theme.palette.text.hint,
  },
}));

export const TracesTableRowImpl = ({
  traceSummary,
  onAddFilter,
  correctedTraceMap,
}) => {
  const classes = useStyles();

  const startTime = moment(traceSummary.timestamp / 1000);

  const correctedTrace = correctedTraceMap[traceSummary.traceId];
  const { spanName, serviceName } = rootServiceAndSpanName(correctedTrace);

  return (
    <Box className={classes.root}>
      <Link to={`/traces/${traceSummary.traceId}`} className={classes.anchor}>
        <Grid container spacing={0} className={classes.data}>
          <Box
            position="absolute"
            width={`${traceSummary.width}%`}
            height="100%"
            className={classes.durationBar}
            style={{
              backgroundColor: selectColorByInfoClass(traceSummary.infoClass),
            }}
            data-testid="duration-bar"
          />
          <Grid item xs={3} className={classes.dataCell}>
            <Box className={classes.serviceName} data-testid="service-name">
              {`${serviceName}`}
            </Box>
            <Box className={classes.subInfo} data-testid="span-name">
              {`(${spanName})`}
            </Box>
          </Grid>
          <Grid item xs={3} className={classes.dataCell}>
            {traceSummary.traceId}
          </Grid>
          <Grid item xs={3} className={classes.dataCell}>
            <Box>{startTime.format('MM/DD HH:mm:ss:SSS')}</Box>
            <Box className={classes.subInfo}>{`(${startTime.fromNow()})`}</Box>
          </Grid>
          <Grid item xs={3} className={classes.dataCell}>
            {traceSummary.durationStr}
          </Grid>
        </Grid>
      </Link>
      {/* In HTML5, anchor tag including interactive content is invalid.
          So ServiceBadge which has onClick callback cannot be surrounded by Link. */}
      <Box display="flex" flexWrap="wrap" className={classes.badgeRow}>
        {traceSummary.serviceSummaries.map((serviceSummary) => (
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
        ))}
      </Box>
    </Box>
  );
};

TracesTableRowImpl.propTypes = propTypes;

const mapStateToProps = (state) => ({
  correctedTraceMap: state.traces.correctedTraceMap,
});

export default connect(mapStateToProps, null)(withRouter(TracesTableRowImpl));
