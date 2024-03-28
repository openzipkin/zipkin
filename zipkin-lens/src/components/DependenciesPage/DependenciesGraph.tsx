/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
/* eslint-disable no-shadow */

import {
  Box,
  Theme,
  createStyles,
  makeStyles,
  useTheme,
  TextField,
} from '@material-ui/core';
import { Autocomplete } from '@material-ui/lab';
import moment from 'moment';
import React, { useState, useCallback, useMemo } from 'react';
import Dependencies from '../../models/Dependencies';
import NodeDetailData from './NodeDetailData';
import { Edge } from './types';
import VizceralWrapper from './VizceralWrapper';
import { getTheme } from '../../util/theme';

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

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    detailWrapper: {
      flex: '0 0 420px',
      width: 420,
      borderLeft: `1px solid ${theme.palette.divider}`,
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
    colorConnectionLine:
      getTheme() === 'dark'
        ? theme.palette.grey['A700']
        : theme.palette.grey[800],
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

  const [filter, setFilter] = useState<string | null>();
  const handleFilterChange = useCallback(
    (_event: any, value: string | null) => {
      setFilter(value);
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

  return (
    <Box
      width="100%"
      height="100%"
      data-testid="dependencies-graph"
      display="flex"
    >
      <Box flex="1 1" position="relative">
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
        <Box position="absolute" left={20} top={20} width={300}>
          <Autocomplete
            value={filter}
            onChange={handleFilterChange}
            options={nodes.map((node) => node.name)}
            fullWidth
            renderInput={(params) => (
              <TextField
                {...params}
                variant="outlined"
                label="Filter"
                placeholder="Select..."
                size="small"
              />
            )}
          />
        </Box>
      </Box>
      {focusedNodeName ? (
        <Box className={classes.detailWrapper}>
          <NodeDetailData
            serviceName={focusedNodeName}
            targetEdges={targetEdges}
            sourceEdges={sourceEdges}
          />
        </Box>
      ) : null}
    </Box>
  );
};

export default DependenciesGraph;
