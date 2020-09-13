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

/* eslint-disable no-shadow */

import {
  Box,
  Grid,
  Theme,
  createStyles,
  makeStyles,
  useTheme,
} from '@material-ui/core';
import moment from 'moment';
import React, { CSSProperties, useState, useCallback, useMemo } from 'react';
import ReactSelect, { ValueType, ActionMeta } from 'react-select';
import { AutoSizer } from 'react-virtualized';

import Edge from './Edge';
import NodeDetailData from './NodeDetailData';
import VizceralWrapper from './VizceralWrapper';
import Dependencies from '../../models/Dependencies';

// These filter functions use any type because they are passed directly to untyped JS code.
const filterConnections = (object: any, value: any) => {
  if (!value) {
    return true;
  }
  if (object.name === value) {
    return true;
  }
  return object.source.name === value || object.target.name === value;
};

const filterNodes = (object: any, value: any) => {
  if (!value) {
    return true;
  }
  if (object.name === value) {
    return true;
  }
  return (
    object.incomingConnections.find(
      (conn: any) => conn.source.name === value,
    ) ||
    object.outgoingConnections.find((conn: any) => conn.target.name === value)
  );
};

// Export for testing.
export const getNodesAndEdges = (dependencies: Dependencies) => {
  const nodes: { name: string }[] = [];
  const edges: Edge[] = [];

  dependencies.forEach((edge) => {
    const nodeNames = nodes.map((node) => node.name);

    if (!nodeNames.includes(edge.parent)) {
      nodes.push({ name: edge.parent });
    }
    if (!nodeNames.includes(edge.child)) {
      nodes.push({ name: edge.child });
    }

    edges.push({
      source: edge.parent,
      target: edge.child,
      metrics: {
        normal: edge.callCount || 0,
        danger: edge.errorCount || 0,
      },
    });
  });
  return { nodes, edges };
};

// IntelliJ may miss on "Unused property" analysis. Double-check here as we don't test CSS:
// https://github.com/JedWatson/react-select/blob/cba15309c4d7523ab6a785c8d5c0c7ec1048e22f/packages/react-select/src/styles.js#L38-L61
const reactSelectStyles = {
  control: (base: CSSProperties) => ({
    ...base,
    width: '15rem',
  }),
  option: (base: CSSProperties) => ({
    ...base,
    cursor: 'pointer',
  }),
  clearIndicator: (base: CSSProperties) => ({
    ...base,
    cursor: 'pointer',
  }),
  dropdownIndicator: (base: CSSProperties) => ({
    ...base,
    cursor: 'pointer',
  }),
};

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    containerGrid: {
      height: '100%',
    },
    graphItemGrid: {
      height: '100%',
    },
    nodeDetailDataItemGrid: {
      height: '100%',
      boxShadow: theme.shadows[10],
      zIndex: 1,
    },
    vizceralWrapper: {
      // In Firefox, 100% height does not work...
      '& .vizceral': {
        height: '99%',
      },
    },
  }),
);

interface DependenciesGraphProps {
  dependencies: Dependencies;
}

const DependenciesGraph: React.FC<DependenciesGraphProps> = ({
  dependencies,
}) => {
  const classes = useStyles();
  const theme = useTheme();
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

  const [focusedNodeName, setFocusedNodeName] = useState('');

  const [filter, setFilter] = useState('');
  const handleFilterChange = useCallback(
    (
      selected: ValueType<{ value: string }>,
      actionMeta: ActionMeta<{ value: string; label: string }>,
    ) => {
      setFocusedNodeName('');
      if (actionMeta.action === 'clear') {
        setFilter('');
        return;
      }
      if (actionMeta.action === 'select-option') {
        if (selected && 'value' in selected /* Type refinement */) {
          setFilter(selected.value);
        }
      }
    },
    [],
  );

  const { nodes, edges, createdTs } = useMemo(() => {
    const { nodes, edges } = getNodesAndEdges(dependencies);
    return {
      nodes,
      edges,
      createdTs: moment().valueOf(),
    };
  }, [dependencies]);

  const targetEdges = useMemo(() => {
    if (focusedNodeName) {
      return edges.filter((edge) => edge.source === focusedNodeName);
    }
    return [];
  }, [edges, focusedNodeName]);

  const sourceEdges = useMemo(() => {
    if (focusedNodeName) {
      return edges.filter((edge) => edge.target === focusedNodeName);
    }
    return [];
  }, [edges, focusedNodeName]);

  // These filter functions use any type because they are passed directly to untyped JS code.
  const handleObjectHighlight = useCallback(
    (highlightedObject?: any) => {
      if (!highlightedObject) {
        setFocusedNodeName('');
        return;
      }
      if (
        highlightedObject.type === 'node' &&
        highlightedObject.getName() !== focusedNodeName
      ) {
        setFocusedNodeName(highlightedObject.getName());
      }
    },
    [focusedNodeName],
  );

  const maxVolume = useMemo(() => {
    if (edges.length > 0) {
      return edges
        .map((edge) => edge.metrics.normal + edge.metrics.danger)
        .reduce((a, b) => Math.max(a, b));
    }
    return 0;
  }, [edges]);

  const filterOptions = useMemo(
    () =>
      nodes
        .map((node) => ({
          value: node.name,
          label: node.name,
        }))
        .sort((a, b) => a.value.localeCompare(b.value)),
    [nodes],
  );

  return (
    <Box width="100%" height="100%" data-testid="dependencies-graph">
      <Grid container className={classes.containerGrid}>
        <Grid
          item
          xs={focusedNodeName ? 7 : 12}
          className={classes.graphItemGrid}
        >
          <AutoSizer>
            {({ width, height }) => (
              <Box
                width={width}
                height={height}
                position="relative"
                className={classes.vizceralWrapper}
              >
                <VizceralWrapper
                  allowDraggingOfNodes
                  targetFramerate={30}
                  traffic={{
                    renderer: 'region',
                    layout: 'ltrTree',
                    name: 'dependencies-graph',
                    maxVolume: maxVolume * 50,
                    nodes,
                    connections: edges,
                    updated: createdTs,
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
              </Box>
            )}
          </AutoSizer>
          <Box position="absolute" left={30} top={30}>
            <ReactSelect
              isClearable
              options={filterOptions}
              onChange={handleFilterChange}
              value={!filter ? undefined : { value: filter, label: filter }}
              styles={reactSelectStyles}
            />
          </Box>
        </Grid>
        {focusedNodeName ? (
          <Grid item xs={5} className={classes.nodeDetailDataItemGrid}>
            <NodeDetailData
              serviceName={focusedNodeName}
              targetEdges={targetEdges}
              sourceEdges={sourceEdges}
            />
          </Grid>
        ) : null}
      </Grid>
    </Box>
  );
};

export default DependenciesGraph;
