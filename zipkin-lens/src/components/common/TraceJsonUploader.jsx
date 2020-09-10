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
import { t } from '@lingui/macro';
import { useLingui } from '@lingui/react';
import PropTypes from 'prop-types';
import React, { useRef, useCallback } from 'react';
import { useDispatch } from 'react-redux';
import { withRouter } from 'react-router-dom';
import { faUpload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
import Button from '@material-ui/core/Button';
import Tooltip from '@material-ui/core/Tooltip';

import { ensureV2TraceData } from '../../util/trace';
import { loadTrace, loadTraceFailure } from '../../actions/trace-viewer-action';

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
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

const TraceJsonUploader = ({ history }) => {
  const classes = useStyles();
  const dispatch = useDispatch();
  const inputRef = useRef(null);
  const { i18n } = useLingui();

  const handleClick = useCallback(() => {
    if (inputRef.current) {
      inputRef.current.click();
    }
  }, []);

  const handleFileChange = useCallback(
    (event) => {
      const fileReader = new FileReader();

      const goToTraceViewerPage = () => {
        history.push({ pathname: '/traceViewer' });
      };

      fileReader.onload = () => {
        const { result } = fileReader;

        let rawTraceData;
        try {
          rawTraceData = JSON.parse(result);
        } catch (error) {
          dispatch(
            loadTraceFailure(i18n._(t`This file does not contain JSON`)),
          );
          goToTraceViewerPage();
          return;
        }

        try {
          ensureV2TraceData(rawTraceData);
          dispatch(loadTrace(rawTraceData));
        } catch (error) {
          dispatch(loadTraceFailure(i18n._(t`Only V2 format is supported`)));
        }
        goToTraceViewerPage();
      };

      fileReader.onabort = () => {
        dispatch(loadTraceFailure(i18n._(t`Failed to load this file`)));
        goToTraceViewerPage();
      };

      fileReader.onerror = fileReader.onabort;

      const [file] = event.target.files;
      fileReader.readAsText(file);
    },
    [dispatch, history, i18n],
  );

  return (
    <>
      <input
        type="file"
        style={{ display: 'none' }}
        ref={inputRef}
        onChange={handleFileChange}
      />
      <Tooltip title={i18n._(t`Upload JSON`)}>
        <Button
          variant="outlined"
          className={classes.button}
          onClick={handleClick}
        >
          <FontAwesomeIcon icon={faUpload} />
        </Button>
      </Tooltip>
    </>
  );
};

TraceJsonUploader.propTypes = propTypes;

export default withRouter(TraceJsonUploader);
