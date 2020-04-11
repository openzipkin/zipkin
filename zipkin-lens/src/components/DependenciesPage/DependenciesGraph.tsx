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
import React, { CSSProperties } from 'react';
import ReactSelect, { ValueType } from 'react-select';
import { AutoSizer } from 'react-virtualized';
import { Box, Grid, useTheme } from '@material-ui/core';
import { createStyles, makeStyles, Theme } from '@material-ui/core/styles';
import moment from 'moment';

import Dependencies from '../../types/Dependencies';
import VizceralWrapper from './VizceralWrapper';
import NodeDetailData from './NodeDetailData';
import { Edge } from './types';

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

const reactSelectStyles = {
  control: (base: CSSProperties) => ({
    ...base,
    width: '15rem',
  }),
};

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    containerGrid: {
      height: '100%',
    },
    itemGrid: {
      height: '100%',
    },
    searchButton: {
      fontSize: '1.2rem',
      padding: theme.spacing(1),
      minWidth: 0,
      width: 32,
      height: 32,
    },
  }),
);

interface Props {
  dependencies: Dependencies;
}

const DependenciesGraph: React.FC<Props> = ({ dependencies }): JSX.Element => {
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

  const [nodeName, setNodeName] = React.useState('');

  const [filter, setFilter] = React.useState('');
  const handleFilterChange = React.useCallback(
    (selected: ValueType<{ value: string }>) => {
      if (selected && 'value' in selected /* Refinement */) {
        setFilter(selected.value);
      }
    },
    [],
  );

  const { nodes, edges, createdTs } = React.useMemo(() => {
    const nodes = [] as { name: string }[];
    const edges = [] as Edge[];

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

    return { nodes, edges, createdTs: moment().valueOf() };
  }, [dependencies]);

  const targetEdges = React.useMemo(() => {
    if (nodeName) {
      return edges.filter((edge) => edge.source === nodeName);
    }
    return [];
  }, [edges, nodeName]);

  const sourceEdges = React.useMemo(() => {
    if (nodeName) {
      return edges.filter((edge) => edge.target === nodeName);
    }
    return [];
  }, [edges, nodeName]);

  // These filter functions use any type because they are passed directly to untyped JS code.
  const handleObjectHighlight = React.useCallback(
    (highlightedObject?: any) => {
      if (!highlightedObject) {
        setNodeName('');
        return;
      }
      if (
        highlightedObject.type === 'node' &&
        highlightedObject.getName() !== nodeName
      ) {
        setNodeName(highlightedObject.getName());
      }
    },
    [nodeName],
  );

  const maxVolume = React.useMemo(() => {
    if (edges.length > 0) {
      return edges
        .map((edge) => edge.metrics.normal + edge.metrics.danger)
        .reduce((a, b) => Math.max(a, b));
    }
    return 0;
  }, [edges]);

  const filterOptions = React.useMemo(
    () =>
      nodes.map((node) => ({
        value: node.name,
        label: node.name,
      })),
    [nodes],
  );

  return (
    <Box width="100%" height="100%" bgcolor="background.paper">
      <Grid container className={classes.containerGrid}>
        <Grid item xs={nodeName ? 8 : 12} className={classes.itemGrid}>
          <AutoSizer>
            {({ width, height }) => (
              <Box width={width} height={height} position="relative">
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
        {nodeName ? (
          <Grid item xs={4} className={classes.itemGrid}>
            <NodeDetailData
              serviceName={nodeName}
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
