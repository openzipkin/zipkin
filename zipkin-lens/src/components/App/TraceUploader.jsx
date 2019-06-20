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
import React, { useRef } from 'react';
import { connect } from 'react-redux';
import { withRouter } from 'react-router';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import { ensureV2TraceData } from '../../util/trace';
import * as traceViewerActionCreators from '../../actions/trace-viewer-action';

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  loadTrace: PropTypes.func.isRequired,
  loadTraceFailure: PropTypes.func.isRequired,
};

const useStyles = makeStyles({
  button: {
    marginTop: '8px', // for align with TraceID input.
    marginRight: '0.4rem',
    height: '2rem',
    width: '1.4rem',
    minWidth: '1.4rem',
  },
});

const TraceUploader = ({
  history,
  loadTrace,
  loadTraceFailure,
}) => {
  const classes = useStyles();

  const inputRef = useRef(null);

  const goToTraceViewerPage = () => {
    history.push({ pathname: '/zipkin/traceViewer' });
  };

  const handleClick = () => {
    if (inputRef.current) {
      inputRef.current.click();
    }
  };

  const handleFileChange = (event) => {
    const fileReader = new FileReader();

    fileReader.onload = () => {
      const { result } = fileReader;

      let rawTraceData;
      try {
        rawTraceData = JSON.parse(result);
      } catch (error) {
        loadTraceFailure('This file does not contain JSON');
        goToTraceViewerPage();
        return;
      }

      try {
        ensureV2TraceData(rawTraceData);
        loadTrace(rawTraceData);
      } catch (error) {
        loadTraceFailure('Only V2 format is supported');
      }
      goToTraceViewerPage();
    };

    fileReader.onabort = () => {
      loadTraceFailure('Failed to load this file');
      goToTraceViewerPage();
    };

    FileReader.onerror = fileReader.onabort;

    const [file] = event.target.files;
    fileReader.readAsText(file);
  };

  return (
    <Box>
      <input
        type="file"
        style={{ display: 'none' }}
        ref={inputRef}
        onChange={handleFileChange}
      />
      <Button
        variant="outlined"
        className={classes.button}
        onClick={handleClick}
      >
        <Box component="span" className="fas fa-upload" />
      </Button>
    </Box>
  );
};

TraceUploader.propTypes = propTypes;

const mapDispatchToProps = (dispatch) => {
  const { loadTrace, loadTraceFailure } = traceViewerActionCreators;
  return {
    loadTrace: trace => dispatch(loadTrace(trace)),
    loadTraceFailure: errorMessage => dispatch(loadTraceFailure(errorMessage)),
  };
};

export default connect(
  null,
  mapDispatchToProps,
)(withRouter(TraceUploader));
