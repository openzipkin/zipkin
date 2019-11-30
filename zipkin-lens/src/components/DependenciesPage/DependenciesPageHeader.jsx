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
import React from 'react';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';
import { KeyboardDateTimePicker } from '@material-ui/pickers';
import { makeStyles } from '@material-ui/styles';
import TraceJsonUploader from '../Common/TraceJsonUploader';
import TraceIdSearchInput from '../Common/TraceIdSearchInput';

const useStyles = makeStyles(theme => ({
  root: {
    boxShadow: theme.shadows[3],
    zIndex: 1, // for box-shadow
  },
  upperBox: {
    borderBottom: `1px solid ${theme.palette.grey[300]}`,
    paddingLeft: theme.spacing(3),
    paddingRight: theme.spacing(3),
    width: '100%',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  upperRightBox: {
    paddingRight: theme.spacing(3),
    display: 'flex',
    alignItems: 'center',
  },
  searchBox: {
    width: '100%',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    padding: theme.spacing(1),
  },
  dateTimePicker: {
    marginLeft: theme.spacing(1),
    marginRight: theme.spacing(1),
  },
  dateTimePickerInput: {
    fontSize: '1rem',
    height: '1.6rem',
    padding: '0.4rem 0.6rem',
  },
  findButton: {
    fontSize: '1.2rem',
    padding: theme.spacing(1),
    minWidth: 0,
  },
}));

const propTypes = {
  startTime: PropTypes.shape({}).isRequired,
  endTime: PropTypes.shape({}).isRequired,
  onStartTimeChange: PropTypes.func.isRequired,
  onEndTimeChange: PropTypes.func.isRequired,
  onFindButtonClick: PropTypes.func.isRequired,
};

const DependenciesPageHeader = React.memo(({
  startTime,
  endTime,
  onStartTimeChange,
  onEndTimeChange,
  onFindButtonClick,
}) => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      <Box className={classes.upperBox}>
        <Typography variant="h5" className={classes.pageTitle}>
          Dependencies
        </Typography>
        <Box className={classes.upperRightBox}>
          <TraceJsonUploader />
          <TraceIdSearchInput />
        </Box>
      </Box>
      <Box className={classes.searchBox}>
        <KeyboardDateTimePicker
          label="Start Time"
          inputVariant="outlined"
          value={startTime}
          onChange={onStartTimeChange}
          className={classes.dateTimePicker}
          InputProps={{ classes: { input: classes.dateTimePickerInput } }}
        />
        -
        <KeyboardDateTimePicker
          label="End Time"
          inputVariant="outlined"
          value={endTime}
          onChange={onEndTimeChange}
          className={classes.dateTimePicker}
          InputProps={{ classes: { input: classes.dateTimePickerInput } }}
        />
        <Button
          color="primary"
          variant="contained"
          onClick={onFindButtonClick}
          className={classes.findButton}
        >
          <FontAwesomeIcon icon={faSearch} />
        </Button>
      </Box>
    </Box>
  );
});

DependenciesPageHeader.propTypes = propTypes;

export default DependenciesPageHeader;
