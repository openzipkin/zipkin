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
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import ButtonGroup from '@material-ui/core/ButtonGroup';
import grey from '@material-ui/core/colors/grey';
import TimeMarker from './TimeMarker';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  isMiniMapOpen: PropTypes.bool.isRequired,
  onMiniMapToggleButtonClick: PropTypes.func.isRequired,
};

const useStyles = makeStyles({
  root: {
    borderBottom: `1px solid ${grey[300]}`,
    backgroundColor: grey[100],
  },
  textButton: {
    fontSize: '1.2rem',
    minWidth: '2rem',
    width: '2rem',
  },
});

const TraceTimelineHeader = ({
  startTs,
  endTs,
  isMiniMapOpen,
  onMiniMapToggleButtonClick,
}) => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      <Box
        display="flex"
        alignItems="center"
        mt={1}
        mr={1}
        ml={1}
        justifyContent="space-between"
      >
        <Box>
          <ButtonGroup>
            <Button className={classes.textButton}>
              <Box component="span" className="fas fa-angle-up" />
            </Button>
            <Button className={classes.textButton}>
              <Box component="span" className="fas fa-angle-right" />
            </Button>
            <Button className={classes.textButton}>
              <Box component="span" className="fas fa-angle-down" />
            </Button>
            <Button className={classes.textButton}>
              <Box component="span" className="fas fa-angle-left" />
            </Button>
          </ButtonGroup>
        </Box>
        <ButtonGroup variant="contained">
          <Button onClick={onMiniMapToggleButtonClick}>
            {isMiniMapOpen ? 'Hide MiniMap' : 'Show MiniMap'}
          </Button>
          <Button>
            Re-root
          </Button>
          <Button>
            Reset root
          </Button>
        </ButtonGroup>
      </Box>
      <TimeMarker startTs={startTs} endTs={endTs} />
    </Box>
  );
};

TraceTimelineHeader.propTypes = propTypes;

export default TraceTimelineHeader;
