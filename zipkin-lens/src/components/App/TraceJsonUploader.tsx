/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { faUpload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { IconButton, Tooltip } from '@material-ui/core';
import { unwrapResult } from '@reduxjs/toolkit';
import React, { useCallback, useRef } from 'react';
import { useDispatch } from 'react-redux';
import { useHistory } from 'react-router-dom';
import styled from 'styled-components';

import { Trans, useTranslation } from 'react-i18next';
import { setAlert } from './slice';
import { loadJsonTrace } from '../../slices/tracesSlice';

const TraceJsonUploader: React.FC = () => {
  const dispatch = useDispatch();
  const history = useHistory();
  const inputEl = useRef<HTMLInputElement>(null);
  const { t } = useTranslation();

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
      <Tooltip title={<Trans t={t}>Upload JSON</Trans>}>
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
