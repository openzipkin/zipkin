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
import React from 'react';
import { withRouter, RouteComponentProps } from 'react-router-dom';
import {
  Box,
  Typography,
  Button,
  Paper,
  Table,
  TableBody,
  TableRow,
  TableCell,
} from '@material-ui/core';
import { createStyles, makeStyles, Theme } from '@material-ui/core/styles';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSquare, faSearch } from '@fortawesome/free-solid-svg-icons';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';

import { Edge } from './types';
import { selectServiceColor } from '../../colors';

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
      flexGrow: 1,
      overflowY: 'auto',
      borderRadius: 3,
    },
    tableCell: {
      padding: `${theme.spacing(1)}px ${theme.spacing(2)}px`,
    },
  }),
);

interface Props extends RouteComponentProps {
  serviceName: string;
  targetEdges: Edge[];
  sourceEdges: Edge[];
}

const NodeDetailData: React.FC<Props> = ({
  serviceName,
  targetEdges,
  sourceEdges,
  history,
}) => {
  const classes = useStyles();

  const handleSearchTracesButtonClick = React.useCallback(() => {
    const params = new URLSearchParams();
    params.set('serviceName', serviceName);
    history.push({
      pathname: '/',
      search: params.toString(),
    });
  }, [serviceName, history]);

  let content: JSX.Element;
  if (targetEdges.length === 0 && sourceEdges.length === 0) {
    content = <div>no data</div>;
  } else {
    const arr = [] as {
      title: string;
      edges: Edge[];
      selectNodeName: (edge: Edge) => string;
    }[];

    if (targetEdges.length !== 0) {
      arr.push({
        title: 'Uses',
        edges: targetEdges,
        selectNodeName: (edge: Edge) => edge.target,
      });
    }
    if (sourceEdges.length !== 0) {
      arr.push({
        title: 'Used',
        edges: sourceEdges,
        selectNodeName: (edge: Edge) => edge.source,
      });
    }

    content = (
      <>
        {arr.map((e) => (
          <Box height="50%" display="flex" flexDirection="column">
            <Box height="50%" position="relative">
              <Box
                position="absolute"
                top={15}
                left={15}
                display="flex"
                alignItems="center"
              >
                <Box color="text.secondary">
                  <Typography variant="h6">{e.title}</Typography>
                </Box>
                <Box color="text.hint" ml={1}>
                  (traced requests)
                </Box>
              </Box>
              {e.edges.length === 0 ? (
                <Box
                  height="100%"
                  display="flex"
                  alignItems="center"
                  justifyContent="center"
                >
                  <Box
                    bgcolor="grey.800"
                    color="common.white"
                    borderRadius={3}
                    p={1}
                    className={classes.noDataMessage}
                  >
                    NO DATA
                  </Box>
                </Box>
              ) : (
                <ResponsiveContainer>
                  <PieChart>
                    <Pie
                      data={e.edges.map((edge) => ({
                        name: e.selectNodeName(edge),
                        value: edge.metrics.normal + edge.metrics.danger,
                      }))}
                      nameKey="name"
                      dataKey="value"
                      outerRadius={60}
                    >
                      {e.edges.map((edge) => (
                        <Cell
                          key={e.selectNodeName(edge)}
                          fill={selectServiceColor(e.selectNodeName(edge))}
                        />
                      ))}
                    </Pie>
                  </PieChart>
                </ResponsiveContainer>
              )}
            </Box>
            <Paper className={classes.tablePaper}>
              <Table>
                <TableBody>
                  {e.edges.map((edge) => (
                    <TableRow>
                      <TableCell className={classes.tableCell}>
                        <FontAwesomeIcon
                          icon={faSquare}
                          color={selectServiceColor(e.selectNodeName(edge))}
                        />
                        <Box component="span" ml={0.5}>
                          {e.selectNodeName(edge)}
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
      </>
    );
  }

  return (
    <Box height="100%" boxShadow={10} display="flex" flexDirection="column">
      <Box
        pt={2}
        pb={1}
        pr={2}
        pl={2}
        bgcolor="grey.200"
        color="text.secondary"
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
        <Box display="flex" justifyContent="flex-end">
          <Button variant="outlined" onClick={handleSearchTracesButtonClick}>
            <FontAwesomeIcon icon={faSearch} />
            <Box component="span" ml={0.75}>
              Traces
            </Box>
          </Button>
        </Box>
      </Box>
      <Box
        flexGrow={1}
        bgcolor="background.default"
        borderColor="divider"
        borderTop={1}
        marginBottom={2}
      >
        {content}
      </Box>
    </Box>
  );
};

export default withRouter(NodeDetailData);
