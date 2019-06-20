/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import PropTypes from 'prop-types';
import React, { useState } from 'react';
import { connect } from 'react-redux';
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
import { KeyboardDateTimePicker } from '@material-ui/pickers';

import * as globalSearchActionCreators from '../../../actions/global-search-action';
import { globalSearchLookbackConditionPropTypes } from '../../../prop-types';

const lookbackOptions = [
  { value: '1m', label: '1 Minute' },
  { value: '5m', label: '5 Minutes' },
  { value: '15m', label: '15 Minutes' },
  { value: '30m', label: '30 Minutes' },
  { value: '1h', label: '1 Hour' },
  { value: '2h', label: '2 Hours' },
  { value: '6h', label: '6 Hours' },
  { value: '12h', label: '12 Hours' },
  { value: '1d', label: '1 Day' },
  { value: '2d', label: '2 Days' },
  { value: '7d', label: '7 Days' },
  { value: 'custom', label: 'Custom' },
];

const lookbackOptionMap = lookbackOptions.reduce((acc, cur) => {
  acc[cur.value] = cur.label;
  return acc;
}, {});

const lookbackMenuOptions = ['1h', '2h', '6h', '12h'].map(value => ({
  value,
  label: lookbackOptionMap[value],
})).concat([{
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

const propTypes = {
  lookbackCondition: globalSearchLookbackConditionPropTypes.isRequired,
  onChange: PropTypes.func.isRequired,
};

const LookbackCondition = ({
  lookbackCondition,
  onChange,
}) => {
  const classes = useStyles();

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
    onChange({
      value: 'custom',
      startTs: customRange.startTime.valueOf(),
      endTs: customRange.endTime.valueOf(),
    });
  };

  const startTime = moment(lookbackCondition.startTs);
  const endTime = moment(lookbackCondition.endTs);

  let lookbackButtonText = '';
  if (isCustom) {
    const startTimeStr = startTime.format('MMM Do YY, hh:mm');
    const endTimeStr = endTime.format('MMM Do YY, hh:mm');
    lookbackButtonText = `${startTimeStr} to ${endTimeStr}`;
  } else {
    lookbackButtonText = lookbackOptionMap[lookbackCondition.value];
  }

  const renderMenuItems = (lookbackOption) => {
    if (lookbackOption.value === 'more') {
      return (
        <MenuItem onClick={handleMoreClick}>
          {lookbackOption.label}
        </MenuItem>
      );
    }
    return (
      <MenuItem
        onClick={() => {
          setMenuAnchor(null);
          onChange({
            ...lookbackCondition,
            value: lookbackOption.value,
          });
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
        button
        className={classes.fixedLookbackItem}
        onClick={() => {
          setIsModalOpen(false);
          onChange({
            ...lookbackCondition,
            value: lookbackOption.value,
          });
        }}
      >
        <ListItemText primary={lookbackOption.label} />
      </ListItem>
    );
  };

  return (
    <Box minHeight="100%" maxHeight="10rem">
      <Button
        color="primary"
        variant="contained"
        onClick={handleButtonClick}
        className={classes.lookbackButton}
      >
        {lookbackButtonText}
      </Button>
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        { lookbackMenuOptions.map(renderMenuItems) }
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
                value={startTime}
                className={classes.timePicker}
                onChange={handleStartTimeChange}
              />
            </Box>
            <Box mb={1.5}>
              <KeyboardDateTimePicker
                label="End Time"
                value={endTime}
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

LookbackCondition.propTypes = propTypes;

const mapStateToProps = state => ({
  lookbackCondition: state.globalSearch.lookbackCondition,
});

const mapDispatchToProps = (dispatch) => {
  const { setLookbackCondition } = globalSearchActionCreators;
  return {
    onChange: lookbackCondition => dispatch(setLookbackCondition(lookbackCondition)),
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(LookbackCondition);
