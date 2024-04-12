/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { makeStyles, TextField } from '@material-ui/core';
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

  const useStyles = makeStyles({
    traceIdSearch: {
      // Questo selettore cambia il colore del bordo al passaggio del mouse
      '&:hover fieldset': {
        borderColor: '#FFF !important',
      },
      '& .Mui-focused fieldset': {
        borderColor: '#FFF !important',
      },
      '& label.Mui-focused': {
        color: '#FFF !important',
      },
    },
  });
  const classes = useStyles();

  return (
    <TextField
      className={classes.traceIdSearch}
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
