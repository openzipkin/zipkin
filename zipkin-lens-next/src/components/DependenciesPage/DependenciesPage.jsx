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
import React, {
  useMemo,
  useCallback,
  useState,
  useEffect,
} from 'react';
import { connect } from 'react-redux';
import { withRouter } from 'react-router';
import { AutoSizer } from 'react-virtualized';
import moment from 'moment';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import CircularProgress from '@material-ui/core/CircularProgress';

import DependenciesGraph from './DependenciesGraph';
import DependenciesPageHeader from './DependenciesPageHeader';
import NodeDetail from './NodeDetail';
import * as dependenciesActionCreators from '../../actions/dependencies-action';
import Graph from '../../util/dependencies-graph';
import ExplainBox from './ExplainBox';
import { buildQueryParameters } from '../../util/api';

const useStyles = makeStyles(theme => ({
  header: {
    borderBottom: `1px solid ${theme.palette.grey[300]}`,
  },
  timePicker: {
    marginLeft: theme.spacing(1),
    marginRight: theme.spacing(1),
  },
  timePickerInput: {
    fontSize: '1rem',
    height: '1.6rem',
    padding: '0.4rem 0.6rem',
  },
  findButton: {
    fontSize: '1.2rem',
    padding: theme.spacing(1),
    minWidth: '10px',
  },
  loadingIndicatorWrapper: {
    width: '100%',
    height: '100%',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
  },
  explainBoxWrapper: {
    width: '100%',
    height: '100%',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
  },
  content: {
    width: '100%',
    height: '100%',
    display: 'flex',
    backgroundColor: theme.palette.background.paper,
  },
  graphWrapper: {
    height: '100%',
  },
  nodeDetailWrapper: {
    width: '30%',
    height: '100%',
  },
}));

const propTypes = {
  isLoading: PropTypes.bool.isRequired,
  dependencies: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
  fetchDependencies: PropTypes.func.isRequired,
  clearDependencies: PropTypes.func.isRequired,
  location: PropTypes.shape({ search: PropTypes.string.isRequired }).isRequired,
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
};

export const DependenciesPageImpl = React.memo(({
  isLoading,
  dependencies,
  fetchDependencies,
  clearDependencies,
  location,
  history,
}) => {
  const classes = useStyles();
  const graph = useMemo(() => new Graph(dependencies), [dependencies]);
  const isGraphExists = graph.allNodes().length !== 0;
  const [nodeName, setNodeName] = useState(null);
  const [timeRange, setTimeRange] = useState({
    startTime: moment().subtract(1, 'days'),
    endTime: moment(),
  });
  const targetEdges = useMemo(
    () => (nodeName ? graph.getTargetEdges(nodeName) : []),
    [nodeName, graph],
  );
  const sourceEdges = useMemo(
    () => (nodeName ? graph.getSourceEdges(nodeName) : []),
    [nodeName, graph],
  );

  const handleStartTimeChange = useCallback((startTime) => {
    setTimeRange({ ...timeRange, startTime });
  }, [timeRange]);

  const handleEndTimeChange = useCallback((endTime) => {
    setTimeRange({ ...timeRange, endTime });
  }, [timeRange]);

  const handleFindButtonClick = useCallback(() => {
    const startTs = timeRange.startTime.valueOf();
    const endTs = timeRange.endTime.valueOf();
    const lookback = endTs - startTs;
    fetchDependencies({ lookback, endTs });
    history.push({
      pathname: '/dependency',
      search: buildQueryParameters({ startTs, endTs }),
    });
  }, [fetchDependencies, timeRange, history]);

  const handleNodeClick = useCallback((newNodeName) => {
    setNodeName(newNodeName);
  }, []);

  useEffect(() => {
    const queryParams = new URLSearchParams(location.search);

    const startTs = queryParams.get('startTs');
    const endTs = queryParams.get('endTs');
    if (startTs && endTs) {
      setTimeRange({
        startTime: moment(parseInt(startTs, 10)),
        endTime: moment(parseInt(endTs, 10)),
      });
      const lookback = endTs - startTs;
      fetchDependencies({ lookback, endTs });
    }
    return clearDependencies;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  let content;
  if (isLoading) {
    content = (
      <Box className={classes.loadingIndicatorWrapper} data-testid="loading-indicator">
        <CircularProgress />
      </Box>
    );
  } else if (!isGraphExists) {
    content = (
      <Box className={classes.explainBoxWrapper} data-testid="explain-box">
        <ExplainBox />
      </Box>
    );
  } else {
    content = (
      <Box className={classes.content}>
        <Box width={nodeName ? '70%' : '100%'} className={classes.graphWrapper} data-testid="dependencies-graph">
          <AutoSizer>
            {
              ({ width, height }) => (
                <Box width={width} height={height}>
                  <DependenciesGraph
                    selectedNodeName={nodeName}
                    onNodeClick={handleNodeClick}
                    edges={graph.allEdges()}
                    nodes={graph.allNodes()}
                    updated={graph.createdTs()}
                  />
                </Box>
              )
            }
          </AutoSizer>
        </Box>
        {
          nodeName ? (
            <Box className={classes.nodeDetailWrapper} data-testid="node-detail">
              <AutoSizer>
                {
                  ({ width, height }) => (
                    <Box width={width} height={height} overflow="auto">
                      <NodeDetail
                        serviceName={nodeName}
                        minHeight={height}
                        targetEdges={targetEdges}
                        sourceEdges={sourceEdges}
                        startTime={timeRange.startTime}
                        endTime={timeRange.endTime}
                      />
                    </Box>
                  )
                }
              </AutoSizer>
            </Box>
          ) : null
        }
      </Box>
    );
  }

  return (
    <>
      <DependenciesPageHeader
        startTime={timeRange.startTime}
        endTime={timeRange.endTime}
        onStartTimeChange={handleStartTimeChange}
        onEndTimeChange={handleEndTimeChange}
        onFindButtonClick={handleFindButtonClick}
      />
      {content}
    </>
  );
});

DependenciesPageImpl.propTypes = propTypes;

const mapStateToProps = state => ({
  isLoading: state.dependencies.isLoading,
  dependencies: state.dependencies.dependencies,
});

const mapDispatchToProps = dispatch => ({
  fetchDependencies: params => dispatch(
    dependenciesActionCreators.fetchDependencies(params),
  ),
  clearDependencies: () => dispatch(dependenciesActionCreators.clearDependencies()),
});

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(withRouter(DependenciesPageImpl));
