import { Trans } from '@lingui/macro';
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

import { faUpload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { IconButton, Tooltip } from '@material-ui/core';
import { unwrapResult } from '@reduxjs/toolkit';
import React, { useCallback, useRef } from 'react';
import { useDispatch } from 'react-redux';
import { useHistory } from 'react-router-dom';
import styled from 'styled-components';

import { setAlert } from './slice';
import { loadJsonTrace } from '../../slices/tracesSlice';

const TraceJsonUploader: React.FC = () => {
  const dispatch = useDispatch();
  const history = useHistory();
  const inputEl = useRef<HTMLInputElement>(null);

  const handleClick = useCallback(() => {
    if (inputEl.current) {
      inputEl.current.click();
    }
  }, []);

  const handleFileChange = useCallback(
    (event) => {
      const [file] = event.target.files;
      dispatch(loadJsonTrace(file))
        .then(unwrapResult)
        .then(({ traceId }) => {
          history.push({
            pathname: `/traces/${traceId}`,
          });
        })
        .catch((err) => {
          dispatch(
            setAlert({
              message: `Failed to load file: ${err.message}`,
              severity: 'error',
            }),
          );
        });
    },
    [dispatch, history],
  );

  return (
    <>
      <FileInput ref={inputEl} onChange={handleFileChange} />
      <Tooltip title={<Trans>Upload JSON</Trans>}>
        <IconButton onClick={handleClick}>
          <FontAwesomeIcon icon={faUpload} size="sm" />
        </IconButton>
      </Tooltip>
    </>
  );
};

export default TraceJsonUploader;

const FileInput = styled.input.attrs({
  type: 'file',
})`
  display: none;
`;
