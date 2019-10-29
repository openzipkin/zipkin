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
import React, {
  useCallback,
  useMemo,
  useReducer,
} from 'react';
import ReactSelect from 'react-select';
import Box from '@material-ui/core/Box';
import { makeStyles } from '@material-ui/styles';

import VizceralExt from './VizceralExt';
import { theme } from '../../colors';

const filterConnections = (object, value) => {
  if (!value) {
    return true;
  }
  if (object.name === value) {
    return true;
  }
  return object.source.name === value || object.target.name === value;
};

const filterNodes = (object, value) => {
  if (!value) {
    return true;
  }
  if (object.name === value) {
    return true;
  }
  return object.incomingConnections.find(conn => conn.source.name === value)
    || object.outgoingConnections.find(conn => conn.target.name === value);
};

const useStyles = makeStyles({
  root: {
    // If height is 100%, scroll bar appears.
    // I don't know that reason...
    height: '99%',
    zIndex: 1,
    position: 'relative',
  },
  selectWrapper: {
    position: 'absolute',
    left: 30,
    top: 30,
  },
});

const reactSelectStyles = {
  control: base => ({
    ...base,
    width: '15rem',
  }),
};

const vizStyle = {
  colorText: theme.palette.primary.contrastText,
  colorTextDisabled: theme.palette.primary.contrastText,
  colorConnectionLine: theme.palette.grey[800],
  colorTraffic: {
    normal: theme.palette.primary.dark,
    warning: theme.palette.secondary.dark,
    danger: theme.palette.secondary.dark,
  },
  colorDonutInternalColor: theme.palette.primary.light,
  colorDonutInternalColorHighlighted: theme.palette.primary.light,
  colorLabelBorder: theme.palette.primary.dark,
  colorLabelText: theme.palette.primary.contrastText,
  colorTrafficHighlighted: {
    normal: theme.palette.primary.main,
  },
};

const propTypes = {
  selectedNodeName: PropTypes.string,
  onNodeClick: PropTypes.func.isRequired,
  edges: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
  nodes: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
  updated: PropTypes.number.isRequired,
};

const defaultProps = {
  selectedNodeName: '',
};

const DependenciesGraph = React.memo(({
  selectedNodeName,
  onNodeClick,
  edges,
  nodes,
  updated,
}) => {
  const classes = useStyles();
  const [filter, selectFilter] = useReducer((_, selected) => (selected ? selected.value : ''), '');

  const handleObjectHighlight = useCallback((highlightedObject) => {
    if (!highlightedObject) {
      onNodeClick(null);
      return;
    }
    if (highlightedObject.type === 'node' && highlightedObject.getName() !== selectedNodeName) {
      onNodeClick(highlightedObject.getName());
    }
  }, [onNodeClick, selectedNodeName]);

  const maxVolume = useMemo(() => {
    if (edges.length > 0) {
      return edges.map(edge => edge.metrics.normal + edge.metrics.danger)
        .reduce((a, b) => Math.max(a, b));
    }
    return 0;
  }, [edges]);

  const filterOptions = useMemo(() => nodes.map(node => ({
    value: node.name,
    label: node.name,
  })), [nodes]);

  return (
    <Box className={classes.root}>
      <VizceralExt
        allowDraggingOfNodes
        targetFramerate={30}
        traffic={{
          renderer: 'region',
          layout: 'ltrTree',
          name: 'dependencies-graph',
          maxVolume: maxVolume * 50,
          nodes,
          connections: edges,
          updated,
        }}
        objectHighlighted={handleObjectHighlight}
        styles={vizStyle}
        key={
          // Normally, when updating filters, Vizsceral will show and hide nodes without relaying
          // them out. For a large dependency graph, this often won't let us see well the
          // information about the filtered nodes, so we prefer to force a relayout any time we
          // update the filter. Changing the key based on the filter like this causes react to
          // destroy and reconstruct the component from scratch, which will have a layout zooming
          // in on the filtered nodes.
          filter
        }
        filters={[
          {
            name: 'shownConnections',
            type: 'connection',
            passes: filterConnections,
            value: filter,
          },
          {
            name: 'shownNodes',
            type: 'node',
            passes: filterNodes,
            value: filter,
          },
        ]}
      />
      <Box className={classes.selectWrapper}>
        <ReactSelect
          isClearable
          options={filterOptions}
          onChange={selectFilter}
          value={!filter ? undefined : { value: filter, label: filter }}
          styles={reactSelectStyles}
        />
      </Box>
    </Box>
  );
});

DependenciesGraph.propTypes = propTypes;
DependenciesGraph.defaultProps = defaultProps;

export default DependenciesGraph;
