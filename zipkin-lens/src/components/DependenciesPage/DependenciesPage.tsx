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

import { faSearch, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { t, Trans } from '@lingui/macro';
import { useLingui } from '@lingui/react';
import {
  Box,
  Button,
  Theme,
  createStyles,
  makeStyles,
} from '@material-ui/core';
import { KeyboardDateTimePicker } from '@material-ui/pickers';
import { MaterialUiPickersDate } from '@material-ui/pickers/typings/date';
import { History, Location } from 'history';
import moment from 'moment';
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { withRouter, RouteComponentProps } from 'react-router-dom';

import DependenciesGraph from './DependenciesGraph';
import { clearAlert, setAlert } from '../App/slice';
import ExplainBox from '../common/ExplainBox';
import {
  clearDependencies,
  loadDependencies,
} from '../../slices/dependenciesSlice';
import { RootState } from '../../store';
import { LoadingIndicator } from '../common/LoadingIndicator';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    dateTimePicker: {
      marginLeft: theme.spacing(1),
      marginRight: theme.spacing(1),
    },
    dateTimePickerInput: {
      fontSize: '1rem',
      height: '1.8rem',
      padding: `${theme.spacing(0.4)}px ${theme.spacing(0.6)}px`,
    },
    searchButton: {
      fontSize: '1.2rem',
      padding: theme.spacing(1),
      minWidth: 0,
      width: 32,
      height: 32,
    },
  }),
);

interface DependenciesPageProps extends RouteComponentProps {}

const useTimeRange = (history: History, location: Location) => {
  const setTimeRange = useCallback(
    (timeRange: { startTime: moment.Moment; endTime: moment.Moment }) => {
      const ps = new URLSearchParams(location.search);
      ps.set('startTime', timeRange.startTime.valueOf().toString());
      ps.set('endTime', timeRange.endTime.valueOf().toString());
      history.push({
        pathname: location.pathname,
        search: ps.toString(),
      });
    },
    [history, location.pathname, location.search],
  );

  const timeRange = useMemo(() => {
    const ps = new URLSearchParams(location.search);
    const startTimeStr = ps.get('startTime');
    let startTime;
    if (startTimeStr) {
      startTime = moment(parseInt(startTimeStr, 10));
    }

    const endTimeStr = ps.get('endTime');
    let endTime;
    if (endTimeStr) {
      endTime = moment(parseInt(endTimeStr, 10));
    }

    return {
      startTime,
      endTime,
    };
  }, [location.search]);

  return { timeRange, setTimeRange };
};

const useFetchDependencies = (timeRange: {
  startTime?: moment.Moment;
  endTime?: moment.Moment;
}) => {
  const dispatch = useDispatch();

  useEffect(() => {
    if (!timeRange.startTime || !timeRange.endTime) {
      dispatch(clearDependencies());
      return;
    }
    const lookback = timeRange.endTime.diff(timeRange.startTime);
    dispatch(
      loadDependencies({ lookback, endTs: timeRange.endTime.valueOf() }),
    );
  }, [dispatch, timeRange.endTime, timeRange.startTime]);
};

const DependenciesPageImpl: React.FC<DependenciesPageProps> = ({
  history,
  location,
}) => {
  const classes = useStyles();
  const { i18n } = useLingui();
  const dispatch = useDispatch();

  // tempTimeRange manages a time range which is inputted in the form.
  // On the other hand, timeRange is a time range obtained from the URL search params.
  const { timeRange, setTimeRange } = useTimeRange(history, location);
  const [tempTimeRange, setTempTimeRange] = useState({
    startTime: timeRange.startTime
      ? timeRange.startTime
      : moment().subtract({ days: 1 }),
    endTime: timeRange.endTime ? timeRange.endTime : moment(),
  });
  useFetchDependencies(timeRange);

  const { isLoading, dependencies, error } = useSelector(
    (state: RootState) => state.dependencies,
  );

  useEffect(() => {
    if (error) {
      dispatch(
        setAlert({
          message: 'Failed to load dependencies...',
          severity: 'error',
        }),
      );
    } else {
      dispatch(clearAlert());
    }
  }, [error, dispatch]);

  const handleStartTimeChange = useCallback(
    (startTime: MaterialUiPickersDate) => {
      if (startTime) {
        setTempTimeRange({ ...tempTimeRange, startTime });
      }
    },
    [tempTimeRange],
  );

  const handleEndTimeChange = useCallback(
    (endTime: MaterialUiPickersDate) => {
      if (endTime) {
        setTempTimeRange({ ...tempTimeRange, endTime });
      }
    },
    [tempTimeRange],
  );

  const handleSearchButtonClick = useCallback(() => {
    setTimeRange(tempTimeRange);
  }, [setTimeRange, tempTimeRange]);

  useEffect(() => {
    return () => {
      dispatch(clearDependencies());
    };
  }, [dispatch]);

  let content: JSX.Element;
  if (isLoading) {
    content = <LoadingIndicator />;
  } else if (dependencies.length > 0) {
    content = <DependenciesGraph dependencies={dependencies} />;
  } else {
    content = (
      <ExplainBox
        icon={faProjectDiagram}
        headerText={<Trans>Search Dependencies</Trans>}
        text={
          <Trans>
            Please select the start and end time. Then, click the search button.
          </Trans>
        }
      />
    );
  }

  return (
    <Box
      width="100%"
      height="calc(100vh - 64px)"
      display="flex"
      flexDirection="column"
    >
      <Box bgcolor="background.paper" boxShadow={3} p={3}>
        <Box display="flex" justifyContent="center" alignItems="center">
          <KeyboardDateTimePicker
            label={i18n._(t`Start Time`)}
            inputVariant="outlined"
            value={tempTimeRange.startTime}
            onChange={handleStartTimeChange}
            format="MM/DD/YYYY HH:mm:ss"
            className={classes.dateTimePicker}
            InputProps={{ classes: { input: classes.dateTimePickerInput } }}
          />
          -
          <KeyboardDateTimePicker
            label={i18n._(t`End Time`)}
            inputVariant="outlined"
            value={tempTimeRange.endTime}
            onChange={handleEndTimeChange}
            format="MM/DD/YYYY HH:mm:ss"
            className={classes.dateTimePicker}
            InputProps={{ classes: { input: classes.dateTimePickerInput } }}
          />
          <Button
            color="primary"
            variant="contained"
            onClick={handleSearchButtonClick}
            className={classes.searchButton}
            data-testid="search-button"
          >
            <FontAwesomeIcon icon={faSearch} />
          </Button>
        </Box>
      </Box>
      <Box m={4} flexGrow={1}>
        {content}
      </Box>
    </Box>
  );
};

export default withRouter(DependenciesPageImpl);
