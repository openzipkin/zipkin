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

import { faSquare, faSearch } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  Box,
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Theme,
  Typography,
  createStyles,
  makeStyles,
} from '@material-ui/core';
import React, { useCallback } from 'react';
import { withRouter, RouteComponentProps } from 'react-router-dom';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';

import Edge from './Edge';
import { selectServiceColor } from '../../constants/color';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    title: {
      overflowWrap: 'break-word',
      flexGrow: 1,
      minWidth: 10, // break-word in flex-box
    },
    searchTracesButton: {
      display: 'flex',
    },
    noDataMessage: {
      opacity: 0.8,
    },
    tablePaper: {
      marginRight: theme.spacing(2),
      marginLeft: theme.spacing(2),
      maxHeight: 180,
      overflowY: 'auto',
      borderRadius: 3,
    },
    tableHeadCell: {
      top: 0,
      position: 'sticky',
      padding: `${theme.spacing(1)}px ${theme.spacing(2)}px`,
      backgroundColor: theme.palette.grey[200],
      zIndex: 1,
    },
    tableCell: {
      wordBreak: 'break-all',
      padding: `${theme.spacing(1)}px ${theme.spacing(2)}px`,
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
      <Box
        pt={2}
        pb={1}
        pr={2}
        pl={2}
        bgcolor="grey.200"
        color="text.secondary"
        display="flex"
        justifyContent="space-between"
      >
        <Box display="flex" alignItems="center">
          <Box mr={0.75}>
            <FontAwesomeIcon
              icon={faSquare}
              size="lg"
              color={selectServiceColor(serviceName)}
            />
          </Box>
          <Typography variant="h5" className={classes.title}>
            {serviceName}
          </Typography>
        </Box>
        <Button
          variant="outlined"
          onClick={handleSearchTracesButtonClick}
          data-testid="search-traces-button"
        >
          <FontAwesomeIcon icon={faSearch} />
          <Box component="span" ml={0.75}>
            Traces
          </Box>
        </Button>
      </Box>
      <Box
        height={100}
        flexGrow={1}
        bgcolor="background.paper"
        borderColor="divider"
        borderTop={1}
        paddingBottom={2}
        overflow="auto"
      >
        {shownDataList.map((d) => (
          <Box display="flex" flexDirection="column">
            <Box height={180} position="relative">
              <Box
                position="absolute"
                top={15}
                left={15}
                display="flex"
                alignItems="center"
              >
                <Box color="text.secondary">
                  <Typography variant="h6">{d.title}</Typography>
                </Box>
                <Box color="text.hint" ml={1}>
                  (traced requests)
                </Box>
              </Box>
              <Box position="absolute" bottom={10} right={15}>
                <Box color="text.hint" ml={1}>
                  {d.formatter(d.edges.length)}
                </Box>
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
                    outerRadius={60}
                  >
                    {d.edges.map((edge) => (
                      <Cell
                        key={d.selectNodeName(edge)}
                        fill={selectServiceColor(d.selectNodeName(edge))}
                      />
                    ))}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
            </Box>
            <Paper className={classes.tablePaper}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell className={classes.tableHeadCell}>
                      Service Name
                    </TableCell>
                    <TableCell className={classes.tableHeadCell}>
                      Call
                    </TableCell>
                    <TableCell className={classes.tableHeadCell}>
                      Error
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {d.edges.map((edge) => (
                    <TableRow>
                      <TableCell className={classes.tableCell}>
                        <FontAwesomeIcon
                          icon={faSquare}
                          color={selectServiceColor(d.selectNodeName(edge))}
                        />
                        <Box component="span" ml={0.5}>
                          {d.selectNodeName(edge)}
                        </Box>
                      </TableCell>
                      <TableCell className={classes.tableCell}>
                        {edge.metrics.normal}
                      </TableCell>
                      <TableCell className={classes.tableCell}>
                        {edge.metrics.danger}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          </Box>
        ))}
      </Box>
    </Box>
  );
};

export default withRouter(NodeDetailDataImpl);
