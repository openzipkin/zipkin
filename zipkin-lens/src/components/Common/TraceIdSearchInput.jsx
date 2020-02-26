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
import PropTypes from 'prop-types';
import React, { useState, useCallback } from 'react';
import { withRouter } from 'react-router';
import { makeStyles } from '@material-ui/styles';
import TextField from '@material-ui/core/TextField';
import Tooltip from '@material-ui/core/Tooltip';

const useStyles = makeStyles({
  input: {
    fontSize: '1rem',
    height: '1.6rem',
    padding: '0.2rem 0.2rem',
  },
});

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
};

const TraceIdSearchInput = ({ history }) => {
  const classes = useStyles();

  const [traceId, setTraceId] = useState('');

  const handleChange = useCallback((event) => {
    setTraceId(event.target.value);
  }, []);

  const handleKeyDown = useCallback((event) => {
    if (event.key === 'Enter') {
      history.push({
        pathname: `/traces/${traceId}`,
      });
    }
  }, [history, traceId]);

  return (
    <Tooltip title="Search by Trace ID">
      <TextField
        label="Trace ID"
        value={traceId}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        margin="normal"
        variant="outlined"
        placeholder="trace id..."
        InputLabelProps={{
          shrink: true,
        }}
        InputProps={{
          classes: {
            input: classes.input,
          },
        }}
      />
    </Tooltip>
  );
};

TraceIdSearchInput.propTypes = propTypes;

export default withRouter(TraceIdSearchInput);
