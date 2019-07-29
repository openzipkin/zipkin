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
import React, { useCallback } from 'react';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Grid from '@material-ui/core/Grid';
import grey from '@material-ui/core/colors/grey';

import { sortingMethods } from './util';

const useStyles = makeStyles({
  root: {
    backgroundColor: grey[100],
    textTransform: 'uppercase',
    color: grey[700],
    borderBottom: `1px solid ${grey[300]}`,
  },
  cell: {
    paddingLeft: '1rem',
    paddingRight: '1rem',
    paddingTop: '0.8rem',
    paddingBottom: '0.8rem',
  },
  clickable: {
    cursor: 'pointer',
  },
});

const propTypes = {
  sortingMethod: PropTypes.string.isRequired,
  onSortingMethodChange: PropTypes.func.isRequired,
};

const TracesTableHead = ({ sortingMethod, onSortingMethodChange }) => {
  const classes = useStyles();

  const handleStartTimeClick = useCallback(() => {
    switch (sortingMethod) {
      case sortingMethods.OLDEST_FIRST:
        onSortingMethodChange(sortingMethods.NEWEST_FIRST);
        break;
      case sortingMethods.NEWEST_FIRST:
        onSortingMethodChange(sortingMethods.OLDEST_FIRST);
        break;
      default:
        onSortingMethodChange(sortingMethods.NEWEST_FIRST);
        break;
    }
  }, [sortingMethod, onSortingMethodChange]);

  const handleDurationClick = useCallback(() => {
    switch (sortingMethod) {
      case sortingMethods.SHORTEST_FIRST:
        onSortingMethodChange(sortingMethods.LONGEST_FIRST);
        break;
      case sortingMethods.LONGEST_FIRST:
        onSortingMethodChange(sortingMethods.SHORTEST_FIRST);
        break;
      default:
        onSortingMethodChange(sortingMethods.LONGEST_FIRST);
        break;
    }
  }, [sortingMethod, onSortingMethodChange]);

  return (
    <Grid container spacing={0} className={classes.root}>
      <Grid item xs={3} className={classes.cell}>
        Root
      </Grid>
      <Grid item xs={3} className={classes.cell}>
        Trace ID
      </Grid>
      <Grid
        item
        xs={3}
        className={`${classes.cell} ${classes.clickable}`}
        onClick={handleStartTimeClick}
        data-test="start-time"
      >
        Start Time
        &nbsp;
        {sortingMethod === sortingMethods.OLDEST_FIRST && <Box component="span" className="fas fa-arrow-up" />}
        {sortingMethod === sortingMethods.NEWEST_FIRST && <Box component="span" className="fas fa-arrow-down" />}
      </Grid>
      <Grid
        item
        xs={3}
        className={`${classes.cell} ${classes.clickable}`}
        onClick={handleDurationClick}
        data-test="duration"
      >
        Duration
        &nbsp;
        {sortingMethod === sortingMethods.LONGEST_FIRST && <Box component="span" className="fas fa-arrow-up" />}
        {sortingMethod === sortingMethods.SHORTEST_FIRST && <Box component="span" className="fas fa-arrow-down" />}
      </Grid>
    </Grid>
  );
};

TracesTableHead.propTypes = propTypes;

export default TracesTableHead;
