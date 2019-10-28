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
import React, { useMemo, useCallback } from 'react';
import { PieChart, Pie, Cell } from 'recharts';
import { AutoSizer } from 'react-virtualized';
import Box from '@material-ui/core/Box';
import { makeStyles } from '@material-ui/styles';

import { selectServiceColor } from '../../colors';

const useStyles = makeStyles({
  root: {
    position: 'relative',
    width: '100%',
    paddingTop: '100%',
  },
  content: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
  },
});

const propTypes = {
  edgeNames: PropTypes.arrayOf(PropTypes.string).isRequired,
  edgeData: PropTypes.arrayOf(PropTypes.number).isRequired,
};

const DoughnutGraph = React.memo(({ edgeNames, edgeData }) => {
  const classes = useStyles();
  const data = useMemo(() => edgeNames.map((name, index) => ({
    name,
    value: edgeData[index],
  })), [edgeNames, edgeData]);

  const renderLabel = useCallback(({ index }) => data[index].name, [data]);

  return (
    <Box className={classes.root}>
      <Box className={classes.content}>
        <AutoSizer>
          {
            ({ width }) => (
              <PieChart width={width} height={width}>
                <Pie
                  data={data}
                  nameKey="name"
                  dataKey="value"
                  cx="50%"
                  cy="50%"
                  outerRadius={60}
                  label={renderLabel}
                >
                  {
                    data.map(entry => (
                      <Cell key={entry.name} fill={selectServiceColor(entry.name)} />
                    ))
                  }
                </Pie>
              </PieChart>
            )
          }
        </AutoSizer>
      </Box>
    </Box>
  );
});

DoughnutGraph.propTypes = propTypes;

export default DoughnutGraph;
