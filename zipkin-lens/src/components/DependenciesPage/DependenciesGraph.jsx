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
import React, { useState, useCallback, useMemo } from 'react';
import ReactSelect from 'react-select';
import _ from 'lodash';
import Box from '@material-ui/core/Box';
import { makeStyles } from '@material-ui/styles';

import VizceralExt from './VizceralExt';
import { theme } from '../../colors';

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
  selectedNodeName: PropTypes.string.isRequired,
  onNodeClick: PropTypes.func.isRequired,
  edges: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
  nodes: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
};

const DependenciesGraph = React.memo(({
  selectedNodeName,
  onNodeClick,
  edges,
  nodes,
}) => {
  const classes = useStyles();
  const [filter, setFilter] = useState('');

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
      const maxVolumeEdge = _.maxBy(
        edges,
        edge => edge.metrics.normal + edge.metrics.danger,
      );
      return maxVolumeEdge.metrics.normal + maxVolumeEdge.metrics.danger;
    }
    return 0;
  }, [edges]);

  const filterOptions = useMemo(() => nodes.map(node => ({
    value: node.name,
    label: node.name,
  })), [nodes]);

  const handleFilterChange = useCallback((selected) => {
    if (!selected) {
      setFilter('');
    } else {
      setFilter(selected.value);
    }
  }, []);

  return (
    <Box className={classes.root}>
      <VizceralExt
        allowDraggingOfNodes
        targetFramerate={30}
        traffic={{
          renderer: 'region',
          layout: 'ltrTree',
          name: 'dependencies-graph',
          updated: new Date().getTime(),
          maxVolume: maxVolume * 50,
          nodes,
          connections: edges,
        }}
        objectHighlighted={handleObjectHighlight}
        match={filter}
        styles={vizStyle}
      />
      <Box className={classes.selectWrapper}>
        <ReactSelect
          isClearable
          options={filterOptions}
          onChange={handleFilterChange}
          value={!filter ? undefined : { value: filter, label: filter }}
          styles={reactSelectStyles}
        />
      </Box>
    </Box>
  );
});

DependenciesGraph.propTypes = propTypes;

export default DependenciesGraph;
