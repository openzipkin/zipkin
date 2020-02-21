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
import React, { useState } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import moment from 'moment';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import Dialog from '@material-ui/core/Dialog';
import DialogTitle from '@material-ui/core/DialogTitle';
import DialogContent from '@material-ui/core/DialogContent';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import Menu from '@material-ui/core/Menu';
import MenuItem from '@material-ui/core/MenuItem';
import Tooltip from '@material-ui/core/Tooltip';
import { KeyboardDateTimePicker } from '@material-ui/pickers';

import { setLookbackCondition } from '../../../actions/global-search-action';

const nonCustomLookbackOptions = [
  [moment.duration(1, 'minutes'), false],
  [moment.duration(5, 'minutes'), true],
  [moment.duration(15, 'minutes'), true],
  [moment.duration(30, 'minutes'), false],
  [moment.duration(1, 'hours'), true],
  [moment.duration(2, 'hours'), true],
  [moment.duration(6, 'hours'), true],
  [moment.duration(12, 'hours'), true],
  [moment.duration(1, 'days'), false],
  [moment.duration(2, 'days'), false],
  [moment.duration(7, 'days'), false],
].map(([duration, quick]) => ({
  value: duration.asMilliseconds(),
  label: duration.humanize(),
  quick,
}));

const lookbackOptions = [
  ...nonCustomLookbackOptions,
  { value: 'custom', label: 'Custom' },
];

const lookbackMenuOptions = nonCustomLookbackOptions
  .filter((option) => option.quick)
  .concat([{
    value: 'more',
    label: 'More...',
  }]);

const useStyles = makeStyles(theme => ({
  lookbackButton: {
    height: '100%',
    whiteSpace: 'nowrap',
    borderRadius: 0,
    boxShadow: 'none',
    borderRight: `1px solid ${theme.palette.grey[600]}`,
  },
  timePicker: {
    display: 'block',
    width: '14rem',
  },
  custom: {
    paddingRight: '2rem',
    borderRight: `1px solid ${theme.palette.grey.A700}`,
  },
  content: {
    display: 'flex',
  },
  fixedLookbackItem: {
    minWidth: '14rem',
  },
}));

const LookbackCondition = () => {
  const classes = useStyles();
  const dispatch = useDispatch();

  const lookbackCondition = useSelector(state => state.globalSearch.lookbackCondition);

  const isCustom = lookbackCondition.value === 'custom';

  const [isModalOpen, setIsModalOpen] = useState(false);

  const [customRange, setCustomRange] = useState({
    startTime: moment(lookbackCondition.startTs),
    endTime: moment(lookbackCondition.endTs),
  });

  const [menuAnchor, setMenuAnchor] = useState(null);

  const handleButtonClick = event => setMenuAnchor(event.currentTarget);
  const handleMenuClose = () => setMenuAnchor(null);

  const handleMoreClick = () => {
    setMenuAnchor(null);
    setIsModalOpen(true);
  };

  const handleModalClose = () => setIsModalOpen(false);

  const handleStartTimeChange = (startTime) => {
    setCustomRange({
      ...customRange,
      startTime,
    });
  };

  const handleEndTimeChange = (endTime) => {
    setCustomRange({
      ...customRange,
      endTime,
    });
  };

  const handleApplyButtonClick = () => {
    setIsModalOpen(false);
    dispatch(setLookbackCondition({
      value: 'custom',
      startTs: customRange.startTime.valueOf(),
      endTs: customRange.endTime.valueOf(),
    }));
  };

  let lookbackButtonText = '';
  if (isCustom) {
    const startTimeStr = moment(lookbackCondition.startTs).format('MMM Do YY, hh:mm');
    const endTimeStr = moment(lookbackCondition.endTs).format('MMM Do YY, hh:mm');
    lookbackButtonText = `${startTimeStr} - ${endTimeStr}`;
  } else {
    lookbackButtonText = moment.duration(lookbackCondition.value).humanize();
  }

  const renderMenuItems = (lookbackOption) => {
    if (lookbackOption.value === 'more') {
      return (
        <MenuItem onClick={handleMoreClick} key={lookbackOption.value}>
          {lookbackOption.label}
        </MenuItem>
      );
    }

    return (
      <MenuItem
        key={lookbackOption.value}
        onClick={() => {
          setMenuAnchor(null);
          dispatch(setLookbackCondition({
            ...lookbackCondition,
            value: lookbackOption.value,
          }));
        }}
      >
        {lookbackOption.label}
      </MenuItem>
    );
  };

  const renderListItems = (lookbackOption) => {
    if (lookbackOption.value === 'custom') {
      return null;
    }
    return (
      <ListItem
        key={lookbackOption.value}
        button
        className={classes.fixedLookbackItem}
        onClick={() => {
          setIsModalOpen(false);
          dispatch(setLookbackCondition({
            ...lookbackCondition,
            value: lookbackOption.value,
          }));
        }}
      >
        <ListItemText primary={lookbackOption.label} />
      </ListItem>
    );
  };

  return (
    <Box minHeight="100%" maxHeight="10rem">
      <Tooltip title="Lookback">
        <Button
          color="primary"
          variant="contained"
          onClick={handleButtonClick}
          className={classes.lookbackButton}
        >
          {lookbackButtonText}
        </Button>
      </Tooltip>
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        {lookbackMenuOptions.map(renderMenuItems)}
      </Menu>
      <Dialog open={isModalOpen} onClose={handleModalClose}>
        <DialogTitle>
          Lookback
        </DialogTitle>
        <DialogContent className={classes.content}>
          <Box className={classes.custom}>
            <Box mb={3}>
              <KeyboardDateTimePicker
                label="Start Time"
                value={customRange.startTime}
                className={classes.timePicker}
                onChange={handleStartTimeChange}
              />
            </Box>
            <Box mb={1.5}>
              <KeyboardDateTimePicker
                label="End Time"
                value={customRange.endTime}
                className={classes.timePicker}
                onChange={handleEndTimeChange}
              />
            </Box>
            <Button
              variant="contained"
              color="secondary"
              onClick={handleApplyButtonClick}
            >
              Apply
            </Button>
          </Box>
          <Box pl={2} display="flex">
            <List>
              {
                lookbackOptions.slice(0, Math.ceil(lookbackOptions.length / 2)).map(renderListItems)
              }
            </List>
            <List>
              {
                lookbackOptions.slice(Math.ceil(lookbackOptions.length / 2)).map(renderListItems)
              }
            </List>
          </Box>
        </DialogContent>
      </Dialog>
    </Box>
  );
};

export default LookbackCondition;
