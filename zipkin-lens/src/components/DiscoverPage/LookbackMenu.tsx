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
  ClickAwayListener,
  Grid,
  List,
  ListItem,
  ListItemText,
  Paper,
  TextField,
  Theme,
  createStyles,
  makeStyles,
} from '@material-ui/core';
import { KeyboardDateTimePicker } from '@material-ui/pickers';
import { MaterialUiPickersDate } from '@material-ui/pickers/typings/date';
import moment, { Moment } from 'moment';
import React, { useCallback, useState } from 'react';

import { fixedLookbackMap, FixedLookbackValue, Lookback } from './lookback';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    root: {
      position: 'absolute',
      top: 45,
      right: 0,
      height: 420,
      width: 600,
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

const initialMillis = (lookback: Lookback): number => {
  if (lookback.type === 'millis') {
    return lookback.value;
  }
  return 0;
};

const initialStartTime = (lookback: Lookback): Moment => {
  if (lookback.type === 'range') {
    return lookback.startTime;
  }
  return moment().subtract(1, 'h');
};

const initialEndTime = (lookback: Lookback): Moment => {
  if (lookback.type === 'range') {
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
    setIsOpeningDialog(false);
  }, []);

  const handleOutsideClick = useCallback(() => {
    if (!isOpeningDialog) {
      close();
    }
  }, [close, isOpeningDialog]);

  // For Millis Lookback
  const [millis, setMillis] = useState(initialMillis(lookback));
  // Range Lookback
  const [startTime, setStartTime] = useState(initialStartTime(lookback));
  const [endTime, setEndTime] = useState(initialEndTime(lookback));

  const handleMillisChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setMillis(parseInt(event.target.value, 10));
    },
    [],
  );

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

  const handleMillisApplyButtonClick = useCallback(() => {
    onChange({
      type: 'millis',
      endTime: moment(),
      value: millis,
    });
    close();
  }, [close, millis, onChange]);

  const handleRangeApplyButtonClick = useCallback(() => {
    onChange({
      type: 'range',
      startTime,
      endTime,
    });
    close();
  }, [startTime, endTime, onChange, close]);

  return (
    <ClickAwayListener onClickAway={handleOutsideClick}>
      <Paper elevation={5} className={classes.root}>
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
                Millis Lookback
              </Box>
              <TextField
                label="Milliseconds"
                onChange={handleMillisChange}
                value={millis.toString()}
                variant="outlined"
                size="small"
                type="number"
                inputProps={{
                  min: '0',
                  'data-testid': 'millis-input',
                }}
              />
              <Box display="flex" justifyContent="flex-end" mt={1}>
                <Button
                  variant="contained"
                  color="secondary"
                  onClick={handleMillisApplyButtonClick}
                  data-testid="millis-apply-button"
                >
                  Apply
                </Button>
              </Box>
            </Box>
            <Box p={2} borderTop={1} borderColor="divider">
              <Box fontSize="1.1rem" color="text.secondary" mb={2}>
                Range Lookback
              </Box>
              <Box mb={2}>
                <KeyboardDateTimePicker
                  label="Start Time"
                  inputVariant="outlined"
                  format="MM/DD/YYYY HH:mm:ss"
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
                  format="MM/DD/YYYY HH:mm:ss"
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
                  onClick={handleRangeApplyButtonClick}
                  data-testid="apply-button"
                >
                  Apply
                </Button>
              </Box>
            </Box>
          </Grid>
        </Grid>
      </Paper>
    </ClickAwayListener>
  );
};

export default LookbackMenu;
