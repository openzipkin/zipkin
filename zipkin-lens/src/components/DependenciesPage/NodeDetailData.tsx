/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { faSquare, faSearch } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  Box,
  Button,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Theme,
  Typography,
  createStyles,
  makeStyles,
} from '@material-ui/core';
import React, { useCallback } from 'react';
import { withRouter, RouteComponentProps } from 'react-router-dom';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { Edge } from './types';
import { selectServiceColor } from '../../constants/color';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    title: {
      wordWrap: 'break-word',
      flex: '1 1',
      minWidth: 0,
    },
    table: {
      tableLayout: 'fixed',
    },
    tableRow: {
      '&:first-child > *': {
        borderTop: `1px solid ${theme.palette.divider}`,
      },
    },
    tableCell: {
      wordWrap: 'break-word',
    },
    contentWrapper: {
      flex: '1 1',
      borderTop: `1px solid ${theme.palette.divider}`,
      overflow: 'auto',
      '& > :not(:last-child)': {
        marginBottom: theme.spacing(2),
      },
      paddingBottom: theme.spacing(2),
    },
  }),
);

interface NodeDetailDataProps extends RouteComponentProps {
  serviceName: string;
  targetEdges: Edge[];
  sourceEdges: Edge[];
}

const NodeDetailDataImpl: React.FC<NodeDetailDataProps> = ({
  serviceName,
  targetEdges,
  sourceEdges,
  history,
}) => {
  const classes = useStyles();

  const handleSearchTracesButtonClick = useCallback(() => {
    const params = new URLSearchParams();
    params.set('serviceName', serviceName);
    history.push({
      pathname: '/',
      search: params.toString(),
    });
  }, [serviceName, history]);

  const shownDataList = [] as {
    title: string;
    edges: Edge[];
    selectNodeName: (edge: Edge) => string;
    formatter: (totalEdges: number) => string;
  }[];
  if (targetEdges.length !== 0) {
    shownDataList.push({
      title: 'Uses',
      edges: targetEdges,
      selectNodeName: (edge: Edge) => edge.target,
      formatter: (totalEdges: number) =>
        `This service uses ${totalEdges} service${totalEdges <= 1 ? '' : 's'}`,
    });
  }
  if (sourceEdges.length !== 0) {
    shownDataList.push({
      title: 'Used',
      edges: sourceEdges,
      selectNodeName: (edge: Edge) => edge.source,
      formatter: (totalEdges: number) =>
        `This service is used by ${totalEdges} service${
          totalEdges <= 1 ? '' : 's'
        }`,
    });
  }

  return (
    <Box height="100%" display="flex" flexDirection="column">
      <Box px={2} py={1} display="flex" flex="0 0" alignItems="center">
        <Typography variant="h6" className={classes.title}>
          {serviceName}
        </Typography>
        <Box flex="0 0" ml={0.5}>
          <Button
            variant="outlined"
            onClick={handleSearchTracesButtonClick}
            data-testid="search-traces-button"
            startIcon={<FontAwesomeIcon icon={faSearch} />}
          >
            Traces
          </Button>
        </Box>
      </Box>
      <Box className={classes.contentWrapper}>
        {shownDataList.map((d) => (
          <Box key={d.title}>
            <Box height={240} position="relative">
              <Box position="absolute" top={15} left={15}>
                <Typography variant="body1">
                  {d.title} (traced requests)
                </Typography>
              </Box>
              <Box position="absolute" bottom={15} right={15}>
                <Typography variant="body2" color="textSecondary">
                  {d.formatter(d.edges.length)}
                </Typography>
              </Box>
              <ResponsiveContainer>
                <PieChart>
                  <Pie
                    data={d.edges.map((edge) => ({
                      name: d.selectNodeName(edge),
                      value: edge.metrics.normal + edge.metrics.danger,
                    }))}
                    nameKey="name"
                    dataKey="value"
                    outerRadius={70}
                  >
                    {d.edges.map((edge) => (
                      <Cell
                        key={d.selectNodeName(edge)}
                        fill={selectServiceColor(d.selectNodeName(edge))}
                      />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </Box>
            <Box px={2}>
              <Table size="small" className={classes.table}>
                <TableBody>
                  {d.edges.map((edge) => (
                    <TableRow
                      key={`${edge.source}---${edge.target}`}
                      className={classes.tableRow}
                    >
                      <TableCell className={classes.tableCell}>
                        <FontAwesomeIcon
                          icon={faSquare}
                          color={selectServiceColor(d.selectNodeName(edge))}
                        />
                        <Box component="span" ml={0.5}>
                          {d.selectNodeName(edge)}
                        </Box>
                      </TableCell>
                      <TableCell className={classes.tableCell} align="right">
                        {edge.metrics.normal}
                      </TableCell>
                      <TableCell className={classes.tableCell} align="right">
                        {edge.metrics.danger}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  );
};

export default withRouter(NodeDetailDataImpl);
