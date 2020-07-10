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

import {
  Box,
  Button,
  Grid,
  List,
  ListItem,
  ListItemText,
  Paper,
  Theme,
  createStyles,
  makeStyles,
} from '@material-ui/core';
import { KeyboardDateTimePicker } from '@material-ui/pickers';
import { MaterialUiPickersDate } from '@material-ui/pickers/typings/date';
import moment, { Moment } from 'moment';
import React, { useCallback, useRef, useState } from 'react';
import { useEvent } from 'react-use';

import { fixedLookbackMap, FixedLookbackValue, Lookback } from './lookback';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    root: {
      position: 'absolute',
      top: 35,
      left: 0,
      height: 300,
      width: 500,
      zIndex: theme.zIndex.modal,
    },
    containerGrid: {
      height: '100%',
    },
    fixedLookbackItemGrid: {
      height: '100%',
      overflowY: 'auto',
      borderRight: `1px solid ${theme.palette.divider}`,
    },
    list: {
      padding: 0,
    },
  }),
);

interface LookbackMenuProps {
  close: () => void;
  onChange: (lookback: Lookback) => void;
  lookback: Lookback;
}

const initialStartTime = (lookback: Lookback): Moment => {
  if (lookback.type === 'custom') {
    return lookback.startTime;
  }
  return moment().subtract(1, 'h');
};

const initialEndTime = (lookback: Lookback): Moment => {
  if (lookback.type === 'custom') {
    return lookback.endTime;
  }
  return moment();
};

const LookbackMenu: React.FC<LookbackMenuProps> = ({
  close,
  onChange,
  lookback,
}) => {
  const classes = useStyles();

  // LookbackMenu is closed when click on the outside of the component.
  // This state is needed to prevent LookbackMenu component from closing
  // when the dialog of DateTimePicker is clicked.
  const [isOpeningDialog, setIsOpeningDialog] = useState(false);

  const handleDialogOpen = useCallback(() => {
    setIsOpeningDialog(true);
  }, []);

  const handleDialogClose = useCallback(() => {
    // Use setTimeout to change isOpeningDialog state after
    // handleOutsideClick callback function is executed.
    window.setTimeout(() => {
      setIsOpeningDialog(false);
    }, 0);
  }, []);

  const el = useRef<HTMLDivElement>();

  const handleOutsideClick = useCallback(
    (event: any) => {
      if (
        !isOpeningDialog &&
        (!el.current || !el.current.contains(event.target))
      ) {
        close();
      }
    },
    [close, isOpeningDialog],
  );

  useEvent('click', handleOutsideClick, window, false);

  const [startTime, setStartTime] = useState(initialStartTime(lookback));
  const [endTime, setEndTime] = useState(initialEndTime(lookback));

  const handleStartTimeChange = useCallback((date: MaterialUiPickersDate) => {
    if (date) {
      setStartTime(date);
    }
  }, []);

  const handleEndTimeChange = useCallback((date: MaterialUiPickersDate) => {
    if (date) {
      setEndTime(date);
    }
  }, []);

  const handleListItemClick = (value: FixedLookbackValue) => () => {
    onChange({
      type: 'fixed',
      value,
      endTime: moment(),
    });
    close();
  };

  const handleApplyButtonClick = useCallback(() => {
    onChange({
      type: 'custom',
      startTime,
      endTime,
    });
    close();
  }, [startTime, endTime, onChange, close]);

  return (
    <Paper ref={el} elevation={5} className={classes.root}>
      <Grid container className={classes.containerGrid}>
        <Grid item xs={5} className={classes.fixedLookbackItemGrid}>
          <List className={classes.list}>
            {([
              '1m',
              '5m',
              '15m',
              '30m',
              '1h',
              '2h',
              '3h',
              '6h',
              '12h',
              '1d',
              '2d',
              '7d',
            ] as FixedLookbackValue[]).map((value) => (
              <ListItem
                button
                onClick={handleListItemClick(value)}
                key={value}
                data-testid={`lookback-${value}`}
              >
                <ListItemText primary={fixedLookbackMap[value].display} />
              </ListItem>
            ))}
          </List>
        </Grid>
        <Grid item xs={7}>
          <Box p={2}>
            <Box fontSize="1.1rem" color="text.secondary" mb={2}>
              Custom Lookback
            </Box>
            <Box mb={2}>
              <KeyboardDateTimePicker
                label="Start Time"
                inputVariant="outlined"
                format="MM/DD/YYYY HH:mm:ss:SSS"
                value={startTime}
                onChange={handleStartTimeChange}
                onOpen={handleDialogOpen}
                onClose={handleDialogClose}
                data-testid="date-time-picker"
                size="small"
                fullWidth
              />
            </Box>
            <Box mb={2}>
              <KeyboardDateTimePicker
                label="End Time"
                inputVariant="outlined"
                format="MM/DD/YYYY HH:mm:ss:SSS"
                value={endTime}
                onChange={handleEndTimeChange}
                onOpen={handleDialogOpen}
                onClose={handleDialogClose}
                data-testid="date-time-picker"
                size="small"
                fullWidth
              />
            </Box>
            <Box display="flex" justifyContent="flex-end">
              <Button
                variant="contained"
                color="secondary"
                onClick={handleApplyButtonClick}
                data-testid="apply-button"
              >
                Apply
              </Button>
            </Box>
          </Box>
        </Grid>
      </Grid>
    </Paper>
  );
};

export default LookbackMenu;
