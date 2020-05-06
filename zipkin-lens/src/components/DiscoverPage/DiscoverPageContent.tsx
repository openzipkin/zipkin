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
import React, { useEffect, useCallback, useState, useMemo } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import {
  Box,
  Button,
  TextField,
  CircularProgress,
  Paper,
} from '@material-ui/core';
import { History, Location } from 'history';
import moment from 'moment';
import { createStyles, makeStyles, Theme } from '@material-ui/core/styles';
import { faHistory, faSearch } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import Criterion from './Criterion';
import { Lookback, fixedLookbackMap } from './lookback';
import { clearTraces, loadTraces } from '../../actions/traces-action';
import SearchBar from './SearchBar';
import { RootState } from '../../store';
import ExplainBox from './ExplainBox';
import LookbackMenu from './LookbackMenu';

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

interface Props extends RouteComponentProps {}

// Export for testing
export const useQueryParams = (history: History, location: Location) => {
  const setQueryParams = useCallback(
    (criteria: Criterion[], lookback: Lookback, limit: number) => {
      const params = new URLSearchParams();
      criteria.forEach((criterion) => {
        params.set(criterion.key, criterion.value);
      });
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
    [history, location.pathname],
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
        default:
          ret.push({ key, value });
          break;
      }
    });
    return ret;
  }, [location.search]);

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
) => {
  const params: { [key: string]: string } = {};
  const annotationQuery: string[] = [];
  criteria.forEach((criterion) => {
    switch (criterion.key) {
      case 'serviceName':
      case 'spanName':
      case 'remoteServiceName':
      case 'maxDuration':
      case 'minDuration':
        params[criterion.key] = criterion.value;
        break;
      default:
        // All criterions except serviceName, spanName, remoteServiceName, maxDuration,
        // and minDuration are AnnotationQuery.
        if (criterion.value) {
          annotationQuery.push(`${criterion.key}=${criterion.value}`);
        } else {
          annotationQuery.push(`${criterion.key}`);
        }
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
) => {
  const dispatch = useDispatch();

  useEffect(() => {
    // For searching, lookback and limit are always required.
    // If it doesn't exist, clear traces.
    if (!lookback || !limit) {
      dispatch(clearTraces());
      return;
    }

    const params = buildApiQuery(criteria, lookback, limit);
    dispatch(loadTraces(params));
  }, [criteria, dispatch, limit, lookback]);
};

const DiscoverPageContent: React.FC<Props> = ({ history, location }) => {
  const classes = useStyles();

  const { setQueryParams, criteria, lookback, limit } = useQueryParams(
    history,
    location,
  );

  const [tempCriteria, setTempCriteria] = useState(criteria);
  const [tempLookback, setTempLookback] = useState<Lookback>(
    lookback || {
      type: 'fixed',
      value: '15m',
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
          'MM/DD/YYYY HH:mm:ss',
        )} - ${tempLookback.endTime.format('MM/DD/YYYY HH:mm:ss')}`;
      default:
        return '';
    }
  }, [tempLookback]);

  useFetchTraces(criteria, lookback, limit);

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

  const [isShowingLookbackMenu, setIsShowingLookbackMenu] = useState(
    false,
  );

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
          <Button variant="outlined" onClick={toggleLookbackMenu}>
            <Box mr={0.75}>
              <FontAwesomeIcon icon={faHistory} />
            </Box>
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
