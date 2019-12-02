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
import { withRouter } from 'react-router';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import GlobalSearchConditionList from './GlobalSearchConditionList';
import LimitCondition from './conditions/LimitCondition';
import LookbackCondition from './conditions/LookbackCondition';

const useStyles = makeStyles({
  findButton: {
    minWidth: '2.8rem',
    width: '2.8rem',
    height: '100%',
    fontSize: '1.2rem',
    borderRadius: '0 0.2rem 0.2rem 0',
    boxShadow: 'none',
  },
});

const propTypes = {
  findData: PropTypes.func.isRequired,
};

const GlobalSearch = ({ findData }) => {
  const classes = useStyles();

  return (
    <Box
      display="flex"
      width="100%"
      minHeight="3.8rem"
      maxHeight="10rem"
      boxShadow={3}
      borderRadius="0.2rem"
    >
      <Box display="flex" width="100%">
        <GlobalSearchConditionList />
      </Box>
      <LimitCondition />
      <LookbackCondition />
      <Box
        display="flex"
        alignItems="center"
        minHeight="100%"
        maxheight="10rem"
      >
        <Button
          color="primary"
          variant="contained"
          onClick={findData}
          className={classes.findButton}
        >
          <FontAwesomeIcon icon={faSearch} />
        </Button>
      </Box>
    </Box>
  );
};

GlobalSearch.propTypes = propTypes;

export default withRouter(GlobalSearch);
