/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import { KeyboardDateTimePicker } from '@material-ui/pickers';

import * as globalSearchActionCreators from '../../../actions/global-search-action';
import { globalSearchLookbackConditionPropTypes } from '../../../prop-types';

const lookbackOptions = [
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

const useStyles = makeStyles(theme => ({
  lookbackButton: {
    height: '100%',
    whiteSpace: 'nowrap',
    borderRadius: 0,
    boxShadow: 'none',
  },
  timePicker: {
    display: 'block',
  },
  custom: {
    paddingRight: '2rem',
    borderRight: `1px solid ${theme.palette.grey.A700}`,
  },
  content: {
    display: 'flex',
  },
  fixedLookback: {
    paddingLeft: '2rem',
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

  const [isOpen, setIsOpen] = useState(false);

  const [customRange, setCustomRange] = useState({
    startTime: moment(lookbackCondition.startTs),
    endTime: moment(lookbackCondition.endTs),
  });

  const handleClick = () => setIsOpen(true);
  const handleClose = () => setIsOpen(false);

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
    setIsOpen(false);
    onChange({
      value: 'custom',
      startTs: customRange.startTime.unix(),
      endTs: customRange.endTime.unix(),
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

  return (
    <Box minHeight="100%" maxHeight="10rem">
      <Button
        color="secondary"
        variant="contained"
        onClick={handleClick}
        className={classes.lookbackButton}
      >
        {lookbackButtonText}
      </Button>
      <Dialog open={isOpen} onClose={handleClose}>
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
          <Box className={classes.fixedLookback}>
            <List>
              {
                lookbackOptions.map((opt) => {
                  if (opt.value === 'custom') {
                    return null;
                  }
                  return (
                    <ListItem
                      button
                      className={classes.fixedLookbackItem}
                      onClick={() => {
                        console.log(opt.value);
                        setIsOpen(false);
                        onChange({
                          ...lookbackCondition,
                          value: opt.value,
                        });
                      }}
                    >
                      <ListItemText primary={opt.label} />
                    </ListItem>
                  );
                })
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
