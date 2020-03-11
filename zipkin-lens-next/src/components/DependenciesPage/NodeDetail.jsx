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
import React, { useMemo, useCallback } from 'react';
import { withRouter } from 'react-router';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import Paper from '@material-ui/core/Paper';
import Typography from '@material-ui/core/Typography';
import { makeStyles } from '@material-ui/styles';

import DoughnutGraph from './DoughnutGraph';
import EdgeData from './EdgeData';
import { buildQueryParameters } from '../../util/api';

const useStyles = makeStyles(theme => ({
  root: {
    width: '100%',
    borderLeft: `1px solid ${theme.palette.grey[300]}`,
    backgroundColor: theme.palette.grey[100],
  },
  serviceName: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    borderBottom: `1px solid ${theme.palette.grey[300]}`,
    textTransform: 'uppercase',
    paddingTop: theme.spacing(1.5),
    paddingRight: theme.spacing(1),
    paddingBottom: theme.spacing(1),
    paddingLeft: theme.spacing(1),
  },
  tracedRequests: {
    fontSize: '0.8rem',
    color: theme.palette.text.hint,
  },
  paper: {
    paddingTop: theme.spacing(0.5),
    paddingRight: theme.spacing(1),
    paddingBottom: theme.spacing(0.5),
    paddingLeft: theme.spacing(1),
  },
  relatedServicesBox: {
    padding: theme.spacing(1),
    width: '100%',
  },
  subtitle: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
}));

const propTypes = {
  serviceName: PropTypes.string.isRequired,
  targetEdges: PropTypes.arrayOf(PropTypes.shape({
    target: PropTypes.string.isRequired,
  })).isRequired,
  sourceEdges: PropTypes.arrayOf(PropTypes.shape({
    source: PropTypes.string.isRequired,
  })).isRequired,
  minHeight: PropTypes.number.isRequired,
  startTime: PropTypes.shape({}).isRequired,
  endTime: PropTypes.shape({}).isRequired,
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
};

const NodeDetail = React.memo(({
  serviceName,
  targetEdges,
  sourceEdges,
  minHeight,
  startTime,
  endTime,
  history,
}) => {
  const classes = useStyles();
  const outputEdgeNames = useMemo(
    () => targetEdges.map(edge => edge.target),
    [targetEdges],
  );
  const outputEdgeData = useMemo(
    () => targetEdges.map(edge => edge.metrics.normal + edge.metrics.danger),
    [targetEdges],
  );
  const inputEdgeNames = useMemo(
    () => sourceEdges.map(edge => edge.source),
    [sourceEdges],
  );
  const inputEdgeData = useMemo(
    () => sourceEdges.map(edge => edge.metrics.normal + edge.metrics.danger),
    [sourceEdges],
  );

  const handleSearchTracesButtonClick = useCallback(() => {
    history.push({
      pathname: '/',
      search: buildQueryParameters({
        serviceName,
        startTs: startTime.valueOf(),
        endTs: endTime.valueOf(),
        lookback: 'custom',
      }),
    });
  }, [serviceName, startTime, endTime, history]);

  return (
    <Box minHeight={minHeight} className={classes.root}>
      <Box className={classes.serviceName}>
        <Typography variant="h5">
          {serviceName}
        </Typography>
        <Button color="primary" variant="contained" onClick={handleSearchTracesButtonClick}>
          Search Traces
        </Button>
      </Box>
      <Box className={classes.relatedServicesBox}>
        <Box className={classes.subtitle}>
          <Typography variant="h6">
            USES
          </Typography>
          <Box className={classes.tracedRequests}>
            (traced requests)
          </Box>
        </Box>
        <Paper className={classes.paper}>
          {
            targetEdges.length === 0 ? (
              <Box>Not found...</Box>
            ) : (
              <>
                {
                  targetEdges.map(edge => (
                    <EdgeData
                      key={edge.target}
                      nodeName={edge.target}
                      normalCount={edge.metrics.normal}
                      errorCount={edge.metrics.danger}
                    />
                  ))
                }
                <DoughnutGraph edgeNames={outputEdgeNames} edgeData={outputEdgeData} />
              </>
            )
          }
        </Paper>
      </Box>
      <Box className={classes.relatedServicesBox}>
        <Box className={classes.subtitle}>
          <Typography variant="h6">
            USED
          </Typography>
          <Box className={classes.tracedRequests}>
            (traced requests)
          </Box>
        </Box>
        <Paper className={classes.paper}>
          {
            sourceEdges.length === 0 ? (
              <Box>Not found...</Box>
            ) : (
              <>
                {
                  sourceEdges.map(edge => (
                    <EdgeData
                      key={edge.source}
                      nodeName={edge.source}
                      normalCount={edge.metrics.normal}
                      errorCount={edge.metrics.danger}
                    />
                  ))
                }
                <DoughnutGraph edgeNames={inputEdgeNames} edgeData={inputEdgeData} />
              </>
            )
          }
        </Paper>
      </Box>
    </Box>
  );
});

NodeDetail.propTypes = propTypes;

export default withRouter(NodeDetail);
