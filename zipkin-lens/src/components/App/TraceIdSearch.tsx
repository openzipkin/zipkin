/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { TextField } from '@material-ui/core';
import React, { useCallback, useState } from 'react';
import { useHistory } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

const TraceIdSearch: React.FC = () => {
  const { t } = useTranslation();
  const history = useHistory();

  const [traceId, setTraceId] = useState('');

  const handleChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setTraceId(event.target.value);
    },
    [],
  );

  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLInputElement>) => {
      if (event.key === 'Enter') {
        history.push({
          pathname: `/traces/${traceId}`,
        });
      }
    },
    [history, traceId],
  );

  return (
    <TextField
      label="Search by trace ID"
      value={traceId}
      onChange={handleChange}
      onKeyDown={handleKeyDown}
      variant="outlined"
      size="small"
      placeholder={t(`Trace ID`).toString()}
    />
  );
};

export default TraceIdSearch;
