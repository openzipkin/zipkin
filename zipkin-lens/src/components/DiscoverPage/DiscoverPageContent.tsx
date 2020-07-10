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

/* eslint-disable no-shadow */

import { faHistory, faSearch } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  Box,
  Button,
  CircularProgress,
  Paper,
  TextField,
} from '@material-ui/core';
import moment from 'moment';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useHistory, useLocation } from 'react-router-dom';
import styled from 'styled-components';

import Criterion, { newCriterion } from './Criterion';
import ExplainBox from './ExplainBox';
import LookbackMenu from './LookbackMenu';
import SearchBar from './SearchBar';
import { Lookback, fixedLookbackMap, millisecondsToValue } from './lookback';
import { useUiConfig } from '../UiConfig';
import { clearTraces, loadTraces } from '../../actions/traces-action';
import { RootState } from '../../store';

const TracesTab = require('./TracesTab').default;

const LookbackButton = styled(Button)`
  /* Align LookbackButton height with the TextField height. */
  padding-top: 7.5px;
  padding-bottom: 7.5px;
`;

const SearchButton = styled(Button)`
  height: 60px;
  min-width: 60px;
  color: ${({ theme }) => theme.palette.common.white};
`;

const TracesPaper = styled(Paper)`
  height: 100%;
`;

interface DiscoverPageContentProps {
  autocompleteKeys: string[];
}

// Export for testing
export const useQueryParams = (autocompleteKeys: string[]) => {
  const history = useHistory();
  const location = useLocation();

  const setQueryParams = useCallback(
    (criteria: Criterion[], lookback: Lookback, limit: number) => {
      const params = new URLSearchParams();
      const annotationQuery: string[] = [];
      criteria.forEach((criterion) => {
        // If the key is 'tag' or a string included in autocompleteKeys,
        // the criterion will be included in annotationQuery.
        if (criterion.key === 'tagQuery') {
          annotationQuery.push(criterion.value);
        } else if (autocompleteKeys.includes(criterion.key)) {
          if (criterion.value) {
            annotationQuery.push(`${criterion.key}=${criterion.value}`);
          } else {
            annotationQuery.push(criterion.key);
          }
        } else {
          params.set(criterion.key, criterion.value);
        }
      });
      if (annotationQuery.length > 0) {
        params.set('annotationQuery', annotationQuery.join(' and '));
      }
      switch (lookback.type) {
        case 'fixed':
          params.set('lookback', lookback.value);
          params.set('endTs', lookback.endTime.valueOf().toString());
          break;
        case 'custom':
          params.set('lookback', 'custom');
          params.set('endTs', lookback.endTime.valueOf().toString());
          params.set('startTs', lookback.startTime.valueOf().toString());
          break;
        default:
      }
      params.set('limit', limit.toString());
      history.push({
        pathname: location.pathname,
        search: params.toString(),
      });
    },
    [autocompleteKeys, history, location.pathname],
  );

  const criteria = useMemo(() => {
    const ret: Criterion[] = [];
    const params = new URLSearchParams(location.search);

    params.forEach((value, key) => {
      switch (key) {
        case 'lookback':
        case 'startTs':
        case 'endTs':
        case 'limit':
          break;
        case 'annotationQuery': {
          // Split annotationQuery into keys of autocompleteKeys and the others.
          // If the autocompleteKeys is ['projectID', 'phase'] and the annotationQuery is
          // 'projectID=projectA and phase=BETA and http.path=/api/v1/users and http.method=GET',
          // criterion will be like the following.
          // [
          //   { key: 'tagQuery', value: 'http.path=/api/v1/users and http.method=GET' },
          //   { key: 'projectID', value: 'projectA' },
          //   { key: 'phase', value: 'BETA' },
          // ]
          const tagQuery: string[] = [];
          const exps = value.split(' and ');
          exps.forEach((exp) => {
            const strs = exp.split('=');
            if (strs.length === 0) {
              return;
            }
            const [key, value] = strs;
            if (autocompleteKeys.includes(key)) {
              ret.push(newCriterion(key, value || ''));
            } else {
              tagQuery.push(exp);
            }
          });
          ret.push(newCriterion('tagQuery', tagQuery.join(' and ')));
          break;
        }
        default:
          ret.push(newCriterion(key, value));
          break;
      }
    });
    return ret;
  }, [autocompleteKeys, location.search]);

  const lookback = useMemo<Lookback | undefined>(() => {
    const ps = new URLSearchParams(location.search);
    const lookback = ps.get('lookback');
    if (!lookback) {
      return undefined;
    }
    if (lookback === 'custom') {
      const startTs = ps.get('startTs');
      const endTs = ps.get('endTs');
      if (!endTs || !startTs) {
        return undefined;
      }
      const startTime = moment(parseInt(startTs, 10));
      const endTime = moment(parseInt(endTs, 10));
      return {
        type: 'custom',
        startTime,
        endTime,
      };
    }
    const endTs = ps.get('endTs');
    if (!endTs) {
      return undefined;
    }
    const data = fixedLookbackMap[lookback];
    if (!data) {
      return undefined;
    }
    return {
      type: 'fixed',
      value: data.value,
      endTime: moment(parseInt(endTs, 10)),
    };
  }, [location.search]);

  const limit = useMemo(() => {
    const ps = new URLSearchParams(location.search);
    const limit = ps.get('limit');
    if (!limit) {
      return undefined;
    }
    return parseInt(limit, 10);
  }, [location.search]);

  return {
    setQueryParams,
    criteria,
    lookback,
    limit,
  };
};

// Export for testing
export const parseDuration = (duration: string) => {
  const regex = /^(\d+)(s|ms|us)?$/;
  const match = duration.match(regex);

  if (!match || match.length < 2) {
    return undefined;
  }
  if (match.length === 2 || typeof match[2] === 'undefined') {
    return parseInt(match[1], 10);
  }
  switch (match[2]) {
    case 's':
      return parseInt(match[1], 10) * 1000 * 1000;
    case 'ms':
      return parseInt(match[1], 10) * 1000;
    case 'us':
      return parseInt(match[1], 10);
    default:
      return undefined;
  }
};

// Export for testing
export const buildApiQuery = (
  criteria: Criterion[],
  lookback: Lookback,
  limit: number,
  autocompleteKeys: string[],
) => {
  const params: { [key: string]: string } = {};
  const annotationQuery: string[] = [];
  criteria.forEach((criterion) => {
    if (criterion.key === 'tagQuery') {
      annotationQuery.push(criterion.value);
    } else if (autocompleteKeys.includes(criterion.key)) {
      if (criterion.value) {
        annotationQuery.push(`${criterion.key}=${criterion.value}`);
      } else {
        annotationQuery.push(criterion.key);
      }
    } else if (
      criterion.key === 'minDuration' ||
      criterion.key === 'maxDuration'
    ) {
      const duration = parseDuration(criterion.value);
      if (duration) {
        params[criterion.key] = duration.toString();
      }
    } else {
      params[criterion.key] = criterion.value;
    }
  });
  if (annotationQuery.length > 0) {
    params.annotationQuery = annotationQuery.join(' and ');
  }

  params.endTs = lookback.endTime.valueOf().toString();
  switch (lookback.type) {
    case 'custom': {
      const lb = lookback.endTime.valueOf() - lookback.startTime.valueOf();
      params.lookback = lb.toString();
      break;
    }
    case 'fixed': {
      params.lookback = fixedLookbackMap[lookback.value].duration
        .asMilliseconds()
        .toString();
      break;
    }
    default:
  }

  params.limit = limit.toString();
  return params;
};

const useFetchTraces = (
  autocompleteKeys: string[],
  criteria: Criterion[],
  lookback?: Lookback,
  limit?: number,
) => {
  const dispatch = useDispatch();

  useEffect(() => {
    // For searching, lookback and limit are always required.
    // If it doesn't exist, clear traces.
    if (!lookback || !limit) {
      dispatch(clearTraces());
      return;
    }

    const params = buildApiQuery(criteria, lookback, limit, autocompleteKeys);
    dispatch(loadTraces(params));
  }, [autocompleteKeys, criteria, dispatch, limit, lookback]);
};

const DiscoverPageContent: React.FC<DiscoverPageContentProps> = ({
  autocompleteKeys,
}) => {
  const { setQueryParams, criteria, lookback, limit } = useQueryParams(
    autocompleteKeys,
  );

  const [tempCriteria, setTempCriteria] = useState(criteria);

  const { defaultLookback, queryLimit } = useUiConfig();
  const [tempLookback, setTempLookback] = useState<Lookback>(
    lookback || {
      type: 'fixed',
      // If defaultLookback in config.json is incorrect, use 15m as an initial value.
      value: millisecondsToValue[defaultLookback] || '15m',
      endTime: moment(),
    },
  );
  const [tempLimit, setTempLimit] = useState(limit || queryLimit);

  const lookbackDisplay = useMemo<string>(() => {
    switch (tempLookback.type) {
      case 'fixed':
        return fixedLookbackMap[tempLookback.value].display;
      case 'custom':
        return `${tempLookback.startTime.format(
          'MM/DD/YYYY HH:mm:ss',
        )} - ${tempLookback.endTime.format('MM/DD/YYYY HH:mm:ss')}`;
      default:
        return '';
    }
  }, [tempLookback]);

  useFetchTraces(autocompleteKeys, criteria, lookback, limit);

  const handleLimitChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setTempLimit(parseInt(event.target.value, 10));
    },
    [],
  );

  const searchTraces = useCallback(() => {
    // If the lookback is fixed, need to set the click time to endTime.
    if (tempLookback.type === 'fixed') {
      setQueryParams(
        tempCriteria,
        { ...tempLookback, endTime: moment() },
        tempLimit,
      );
    } else {
      setQueryParams(tempCriteria, tempLookback, tempLimit);
    }
  }, [setQueryParams, tempCriteria, tempLookback, tempLimit]);

  const [traces, isLoadingTraces] = useSelector((state: RootState) => [
    state.traces.traces,
    state.traces.isLoading,
  ]);

  const [isShowingLookbackMenu, setIsShowingLookbackMenu] = useState(false);

  const toggleLookbackMenu = useCallback(() => {
    setIsShowingLookbackMenu((prev) => !prev);
  }, []);

  const closeLookbackMenu = useCallback(() => {
    setIsShowingLookbackMenu(false);
  }, []);

  let content: JSX.Element;

  if (isLoadingTraces) {
    content = (
      <Box
        height="100%"
        display="flex"
        alignItems="center"
        justifyContent="center"
      >
        <CircularProgress />
      </Box>
    );
  } else if (traces.length === 0) {
    content = (
      <Box
        height="100%"
        display="flex"
        alignItems="center"
        justifyContent="center"
      >
        <ExplainBox />
      </Box>
    );
  } else {
    content = (
      <Box height="100%" pt={3} pb={3}>
        <TracesPaper elevation={3}>
          <TracesTab />
        </TracesPaper>
      </Box>
    );
  }

  return (
    <Box pr={3} pl={3} flexGrow={1} display="flex" flexDirection="column">
      <Box display="flex" mb={1.25}>
        <Box mr={1} position="relative">
          <LookbackButton
            variant="outlined"
            onClick={toggleLookbackMenu}
            startIcon={<FontAwesomeIcon icon={faHistory} />}
          >
            {lookbackDisplay}
          </LookbackButton>
          {isShowingLookbackMenu && (
            <LookbackMenu
              close={closeLookbackMenu}
              onChange={setTempLookback}
              lookback={tempLookback}
            />
          )}
        </Box>
        <TextField
          label="Limit"
          type="number"
          variant="outlined"
          value={tempLimit}
          onChange={handleLimitChange}
          size="small"
          inputProps={{
            'data-testid': 'query-limit',
          }}
        />
      </Box>
      <Box display="flex">
        <Box flexGrow={1} mr={1}>
          <SearchBar
            criteria={tempCriteria}
            onChange={setTempCriteria}
            searchTraces={searchTraces}
          />
        </Box>
        <SearchButton
          variant="contained"
          color="primary"
          onClick={searchTraces}
        >
          <FontAwesomeIcon icon={faSearch} size="lg" />
        </SearchButton>
      </Box>
      <Box flexGrow={1}>{content}</Box>
    </Box>
  );
};

export default DiscoverPageContent;
