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
  Theme,
  createStyles,
  makeStyles,
} from '@material-ui/core';
import { History, Location } from 'history';
import moment from 'moment';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router-dom';

import Criterion from './Criterion';
import ExplainBox from './ExplainBox';
import LookbackMenu from './LookbackMenu';
import SearchBar from './SearchBar';
import { Lookback, fixedLookbackMap, millisecondsToValue } from './lookback';
import { useUiConfig } from '../UiConfig';
import { clearTraces, loadTraces } from '../../actions/traces-action';
import { RootState } from '../../store';

const TracesTab = require('./TracesTab').default;

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    limitInput: {
      fontSize: '1rem',
      height: 31,
      padding: `${theme.spacing(0.1)}px ${theme.spacing(1)}px`,
    },
    searchButton: {
      height: 60,
      minWidth: 60,
      color: theme.palette.common.white,
    },
    paper: {
      height: '100%',
    },
  }),
);

interface DiscoverPageContentProps extends RouteComponentProps {
  autocompleteKeys: string[];
}

// Export for testing
export const useQueryParams = (
  history: History,
  location: Location,
  autocompleteKeys: string[],
) => {
  const setQueryParams = useCallback(
    (criteria: Criterion[], lookback: Lookback, limit: number) => {
      const params = new URLSearchParams();
      const annotationQuery: string[] = [];
      criteria.forEach((criterion) => {
        // If the key is 'tag' or a string included in autocompleteKeys,
        // the criterion will be included in annotationQuery.
        if (criterion.key === 'tags') {
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
          //   { key: 'tags', value: 'http.path=/api/v1/users and http.method=GET' },
          //   { key: 'projectID', value: 'projectA' },
          //   { key: 'phase', value: 'BETA' },
          // ]
          const tags: string[] = [];
          const exps = value.split(' and ');
          exps.forEach((exp) => {
            const strs = exp.split('=');
            if (strs.length === 0) {
              return;
            }
            const [key, value] = strs;
            if (autocompleteKeys.includes(key)) {
              ret.push({ key, value: value || '' });
            } else {
              tags.push(exp);
            }
          });
          ret.push({ key: 'tags', value: tags.join(' and ') });
          break;
        }
        default:
          ret.push({ key, value });
          break;
      }
    });
    return ret;
  }, [autocompleteKeys, location.search]);

  const lookback = useMemo<Lookback | null>(() => {
    const ps = new URLSearchParams(location.search);
    const lookback = ps.get('lookback');
    if (!lookback) {
      return null;
    }
    if (lookback === 'custom') {
      const startTs = ps.get('startTs');
      const endTs = ps.get('endTs');
      if (!endTs || !startTs) {
        return null;
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
      return null;
    }
    const data = fixedLookbackMap[lookback];
    if (!data) {
      return null;
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
      return null;
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
export const buildApiQuery = (
  criteria: Criterion[],
  lookback: Lookback,
  limit: number,
  autocompleteKeys: string[],
) => {
  const params: { [key: string]: string } = {};
  const annotationQuery: string[] = [];
  criteria.forEach((criterion) => {
    if (criterion.key === 'tags') {
      annotationQuery.push(criterion.value);
    } else if (autocompleteKeys.includes(criterion.key)) {
      if (criterion.value) {
        annotationQuery.push(`${criterion.key}=${criterion.value}`);
      } else {
        annotationQuery.push(criterion.key);
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
  criteria: Criterion[],
  lookback: Lookback | null,
  limit: number | null,
  autocompleteKeys: string[],
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
  history,
  location,
  autocompleteKeys,
}) => {
  const classes = useStyles();

  const { setQueryParams, criteria, lookback, limit } = useQueryParams(
    history,
    location,
    autocompleteKeys,
  );

  const [tempCriteria, setTempCriteria] = useState(criteria);

  const { defaultLookback } = useUiConfig();
  const [tempLookback, setTempLookback] = useState<Lookback>(
    lookback || {
      type: 'fixed',
      // If defaultLookback in config.json is incorrect, use 15m as an initial value.
      value: millisecondsToValue[defaultLookback] || '15m',
      endTime: moment(),
    },
  );
  const [tempLimit, setTempLimit] = useState(limit || 10);

  const lookbackDisplay = useMemo<string>(() => {
    switch (tempLookback.type) {
      case 'fixed':
        return fixedLookbackMap[tempLookback.value].display;
      case 'custom':
        return `${tempLookback.startTime.format(
          'MM/DD/YYYY HH:mm:ss:SSS',
        )} - ${tempLookback.endTime.format('MM/DD/YYYY HH:mm:ss:SSS')}`;
      default:
        return '';
    }
  }, [tempLookback]);

  useFetchTraces(criteria, lookback, limit, autocompleteKeys);

  const handleLimitChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setTempLimit(parseInt(event.target.value, 10));
    },
    [],
  );

  const handleSearchButtonClick = useCallback(() => {
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
        <Paper className={classes.paper} elevation={3}>
          <TracesTab />
        </Paper>
      </Box>
    );
  }

  return (
    <Box pr={3} pl={3} flexGrow={1} display="flex" flexDirection="column">
      <Box display="flex" mb={1.25}>
        <Box mr={1} position="relative">
          <Button
            variant="outlined"
            onClick={toggleLookbackMenu}
            startIcon={<FontAwesomeIcon icon={faHistory} />}
          >
            {lookbackDisplay}
          </Button>
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
          InputLabelProps={{
            shrink: true,
          }}
          InputProps={{
            classes: {
              input: classes.limitInput,
            },
          }}
        />
      </Box>
      <Box display="flex">
        <Box flexGrow={1} mr={1}>
          <SearchBar criteria={tempCriteria} onChange={setTempCriteria} />
        </Box>
        <Button
          variant="contained"
          color="primary"
          className={classes.searchButton}
          onClick={handleSearchButtonClick}
        >
          <FontAwesomeIcon icon={faSearch} size="lg" />
        </Button>
      </Box>
      <Box flexGrow={1}>{content}</Box>
    </Box>
  );
};

export default withRouter(DiscoverPageContent);
