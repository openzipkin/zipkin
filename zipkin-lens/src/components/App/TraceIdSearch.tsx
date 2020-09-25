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

import { useLingui } from '@lingui/react';
import { TextField } from '@material-ui/core';
import { t } from '@lingui/macro';
import React, { useCallback, useState } from 'react';
import { useHistory } from 'react-router-dom';

const TraceIdSearch: React.FC = () => {
  const { i18n } = useLingui();
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
      placeholder={`${i18n._(t`Trace ID`)}...`}
    />
  );
};

export default TraceIdSearch;
