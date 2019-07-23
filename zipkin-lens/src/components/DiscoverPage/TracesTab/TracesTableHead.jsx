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
import React from 'react';
import { makeStyles } from '@material-ui/styles';
import Grid from '@material-ui/core/Grid';
import grey from '@material-ui/core/colors/grey';

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
});

const TracesTableHead = () => {
  const classes = useStyles();

  return (
    <Grid container spacing={0} className={classes.root}>
      <Grid item xs={3} className={classes.cell}>
        Root
      </Grid>
      <Grid item xs={3} className={classes.cell}>
        Trace ID
      </Grid>
      <Grid item xs={3} className={classes.cell}>
        Start Time
      </Grid>
      <Grid item xs={3} className={classes.cell}>
        Duration
      </Grid>
    </Grid>
  );
};

export default TracesTableHead;
