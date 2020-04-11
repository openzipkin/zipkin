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
import React from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { withRouter, RouteComponentProps } from 'react-router-dom';
import { t } from '@lingui/macro';
import { useLingui } from '@lingui/react';
import { makeStyles, Theme, createStyles } from '@material-ui/core/styles';
import { Box, Typography, Button, CircularProgress } from '@material-ui/core';
import { KeyboardDateTimePicker } from '@material-ui/pickers';
import { MaterialUiPickersDate } from '@material-ui/pickers/typings/date';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import moment from 'moment';
import { History, Location } from 'history';

import ExplainBox from './ExplainBox';
import TraceJsonUploader from '../Common/TraceJsonUploader';
import TraceIdSearchInput from '../Common/TraceIdSearchInput';
import {
  loadDependencies,
  clearDependencies,
} from '../../actions/dependencies-action';
import RootState from '../../types/RootState';
import DependenciesGraph from './DependenciesGraph';

// Do require because tsc cannot find the definition of trans.
// TODO: Give a strict type.
const { Trans } = require('@lingui/macro');

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

interface Props extends RouteComponentProps {}

const useTimeRange = (history: History, location: Location) => {
  const setTimeRange = React.useCallback(
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

  const timeRange = React.useMemo(() => {
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

  React.useEffect(() => {
    if (!timeRange.startTime || !timeRange.endTime) {
      return;
    }
    const lookback = timeRange.endTime.diff(timeRange.startTime);
    dispatch(
      loadDependencies({ lookback, endTs: timeRange.endTime.valueOf() }),
    );
  }, [dispatch, timeRange.endTime, timeRange.startTime]);
};

const DependenciesPage: React.FC<Props> = ({
  history,
  location,
}): JSX.Element => {
  const classes = useStyles();
  const { i18n } = useLingui();
  const dispatch = useDispatch();

  // tempTimeRange manages a time range which is inputted in the form.
  // On the other hand, timeRange is a time range obtained from the URL search params.
  const { timeRange, setTimeRange } = useTimeRange(history, location);
  const [tempTimeRange, setTempTimeRange] = React.useState({
    startTime: timeRange.startTime
      ? timeRange.startTime
      : moment().subtract({ days: 1 }),
    endTime: timeRange.endTime ? timeRange.endTime : moment(),
  });
  useFetchDependencies(timeRange);

  const { isLoading, dependencies } = useSelector(
    (state: RootState) => state.dependencies,
  );

  console.log(isLoading, dependencies);

  const handleStartTimeChange = React.useCallback(
    (startTime: MaterialUiPickersDate) => {
      if (startTime) {
        setTempTimeRange({ ...tempTimeRange, startTime });
      }
    },
    [tempTimeRange],
  );

  const handleEndTimeChange = React.useCallback(
    (endTime: MaterialUiPickersDate) => {
      if (endTime) {
        setTempTimeRange({ ...tempTimeRange, endTime });
      }
    },
    [tempTimeRange],
  );

  const handleSearchButtonClick = React.useCallback(() => {
    setTimeRange(tempTimeRange);
  }, [setTimeRange, tempTimeRange]);

  React.useEffect(() => {
    return () => {
      dispatch(clearDependencies());
    };
  }, [dispatch]);

  let content: JSX.Element;
  if (isLoading) {
    content = (
      <Box
        width="100%"
        height="100%"
        display="flex"
        justifyContent="center"
        alignItems="center"
      >
        <CircularProgress />
      </Box>
    );
  } else if (dependencies.length > 0) {
    content = <DependenciesGraph dependencies={dependencies} />;
  } else {
    content = (
      <Box
        width="100%"
        height="100%"
        display="flex"
        justifyContent="center"
        alignItems="center"
      >
        <ExplainBox />
      </Box>
    );
  }

  return (
    <Box width="100%" height="100vh" display="flex" flexDirection="column">
      <Box boxShadow={3} zIndex={10000}>
        <Box
          pl={3}
          pr={3}
          display="flex"
          justifyContent="space-between"
          alignItems="center"
          color="text.secondary"
        >
          <Typography variant="h5">
            <Trans>Dependencies</Trans>
          </Typography>
          <Box pr={3} display="flex" alignItems="center">
            <TraceJsonUploader />
            <TraceIdSearchInput />
          </Box>
        </Box>
        <Box
          display="flex"
          justifyContent="center"
          alignItems="center"
          pt={0.75}
          pb={0.75}
          borderColor="divider"
          borderTop={1}
        >
          <KeyboardDateTimePicker
            label={i18n._(t`Start Time`)}
            inputVariant="outlined"
            value={tempTimeRange.startTime}
            onChange={handleStartTimeChange}
            format="M/DD/YYYY H:mm:ss"
            className={classes.dateTimePicker}
            InputProps={{ classes: { input: classes.dateTimePickerInput } }}
          />
          -
          <KeyboardDateTimePicker
            label={i18n._(t`End Time`)}
            inputVariant="outlined"
            value={tempTimeRange.endTime}
            onChange={handleEndTimeChange}
            format="M/DD/YYYY H:mm:ss"
            className={classes.dateTimePicker}
            InputProps={{ classes: { input: classes.dateTimePickerInput } }}
          />
          <Button
            color="primary"
            variant="contained"
            onClick={handleSearchButtonClick}
            className={classes.searchButton}
          >
            <FontAwesomeIcon icon={faSearch} />
          </Button>
        </Box>
      </Box>
      <Box flexGrow={1}>{content}</Box>
    </Box>
  );
};

export default withRouter(DependenciesPage);
