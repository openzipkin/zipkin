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
import React, { useMemo } from 'react';
import Box from '@material-ui/core/Box';
import Paper from '@material-ui/core/Paper';
import Typography from '@material-ui/core/Typography';
import { makeStyles } from '@material-ui/styles';

import DoughnutGraph from './DoughnutGraph';
import EdgeData from './EdgeData';

const useStyles = makeStyles(theme => ({
  root: {
    width: '100%',
    borderLeft: `1px solid ${theme.palette.grey[300]}`,
    backgroundColor: theme.palette.grey[100],
  },
  serviceName: {
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
};

const NodeDetail = React.memo(({
  serviceName,
  targetEdges,
  sourceEdges,
  minHeight,
}) => {
  const classes = useStyles();
  const usesEdgeNames = useMemo(
    () => targetEdges.map(edge => edge.target),
    [targetEdges],
  );
  const usesEdgeData = useMemo(
    () => targetEdges.map(edge => edge.metrics.normal + edge.metrics.danger),
    [targetEdges],
  );
  const usedEdgeNames = useMemo(
    () => sourceEdges.map(edge => edge.source),
    [sourceEdges],
  );
  const usedEdgeData = useMemo(
    () => sourceEdges.map(edge => edge.metrics.normal + edge.metrics.danger),
    [sourceEdges],
  );

  return (
    <Box minHeight={minHeight} className={classes.root}>
      <Typography variant="h5" className={classes.serviceName}>
        {serviceName}
      </Typography>
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
                <DoughnutGraph edgeNames={usesEdgeNames} edgeData={usesEdgeData} />
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
                <DoughnutGraph edgeNames={usedEdgeNames} edgeData={usedEdgeData} />
              </>
            )
          }
        </Paper>
      </Box>
    </Box>
  );
});

NodeDetail.propTypes = propTypes;

export default NodeDetail;
