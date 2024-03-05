/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { faProjectDiagram, faSync } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  Box,
  Button,
  Theme,
  createStyles,
  makeStyles,
} from '@material-ui/core';
import { KeyboardDateTimePicker } from '@material-ui/pickers';
import { MaterialUiPickersDate } from '@material-ui/pickers/typings/date';
import moment from 'moment';
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { withRouter, RouteComponentProps } from 'react-router-dom';
import { Trans, useTranslation } from 'react-i18next';
import {
  clearDependencies,
  loadDependencies,
} from '../../slices/dependenciesSlice';
import { RootState } from '../../store';
import { clearAlert, setAlert } from '../App/slice';
import ExplainBox from '../common/ExplainBox';
import { LoadingIndicator } from '../common/LoadingIndicator';
import DependenciesGraph from './DependenciesGraph';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    searchWrapper: {
      backgroundColor: theme.palette.background.paper,
      padding: theme.spacing(2),
      flex: '0 0',
      borderBottom: `1px solid ${theme.palette.divider}`,
    },
    dateTimePicker: {
      marginLeft: theme.spacing(1),
      marginRight: theme.spacing(1),
    },
  }),
);

type DependenciesPageProps = RouteComponentProps;

const useTimeRange = (history: any, location: any) => {
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
  const { t } = useTranslation();
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
        headerText={<Trans t={t}>Search Dependencies</Trans>}
        text={
          <Trans t={t}>
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
      <Box className={classes.searchWrapper}>
        <Box display="flex" justifyContent="center" alignItems="center">
          <Box display="flex" mr={0.5} alignItems="center">
            <KeyboardDateTimePicker
              label={t(`Start Time`)}
              inputVariant="outlined"
              value={tempTimeRange.startTime}
              onChange={handleStartTimeChange}
              format="MM/DD/YYYY HH:mm:ss"
              className={classes.dateTimePicker}
              size="small"
            />
            -
            <KeyboardDateTimePicker
              label={t(`End Time`)}
              inputVariant="outlined"
              value={tempTimeRange.endTime}
              onChange={handleEndTimeChange}
              format="MM/DD/YYYY HH:mm:ss"
              className={classes.dateTimePicker}
              size="small"
            />
          </Box>
          <Button
            color="primary"
            variant="contained"
            onClick={handleSearchButtonClick}
            data-testid="search-button"
            startIcon={<FontAwesomeIcon icon={faSync} />}
          >
            <Trans t={t}>Run Query</Trans>
          </Button>
        </Box>
      </Box>
      <Box flex="1 1" bgcolor="background.paper" overflow="hidden">
        {content}
      </Box>
    </Box>
  );
};

export default withRouter(DependenciesPageImpl);
