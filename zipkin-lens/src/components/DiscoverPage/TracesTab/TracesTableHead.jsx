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
import { Trans } from '@lingui/macro';
import PropTypes from 'prop-types';
import React, { useCallback } from 'react';
import { faArrowUp, faArrowDown } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
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
        <Trans>Root</Trans>
      </Grid>
      <Grid item xs={3} className={classes.cell}>
        <Trans>Trace ID</Trans>
      </Grid>
      <Grid
        item
        xs={3}
        className={`${classes.cell} ${classes.clickable}`}
        onClick={handleStartTimeClick}
        data-testid="start-time"
      >
        <Trans>Start Time</Trans>
        &nbsp;
        {sortingMethod === sortingMethods.OLDEST_FIRST && (
          <FontAwesomeIcon icon={faArrowUp} />
        )}
        {sortingMethod === sortingMethods.NEWEST_FIRST && (
          <FontAwesomeIcon icon={faArrowDown} />
        )}
      </Grid>
      <Grid
        item
        xs={3}
        className={`${classes.cell} ${classes.clickable}`}
        onClick={handleDurationClick}
        data-testid="duration"
      >
        <Trans>Duration</Trans>
        &nbsp;
        {sortingMethod === sortingMethods.LONGEST_FIRST && (
          <FontAwesomeIcon icon={faArrowUp} />
        )}
        {sortingMethod === sortingMethods.SHORTEST_FIRST && (
          <FontAwesomeIcon icon={faArrowDown} />
        )}
      </Grid>
    </Grid>
  );
};

TracesTableHead.propTypes = propTypes;

export default TracesTableHead;
