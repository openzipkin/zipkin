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
import PropTypes from 'prop-types';
import React from 'react';
import {
  faAngleUp,
  faAngleDown,
  faAngleDoubleRight,
  faAngleDoubleLeft,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import ButtonGroup from '@material-ui/core/ButtonGroup';

import TimeMarker from './TimeMarker';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  isRerooted: PropTypes.bool.isRequired,
  isRootedTrace: PropTypes.bool.isRequired,
  onResetRerootButtonClick: PropTypes.func.isRequired,
  isSpanDetailOpened: PropTypes.bool.isRequired,
  onSpanDetailToggle: PropTypes.func.isRequired,
  onCollapseButtonClick: PropTypes.func.isRequired,
  onExpandButtonClick: PropTypes.func.isRequired,
};

const useStyles = makeStyles((theme) => ({
  root: {
    borderBottom: `1px solid ${theme.palette.grey[300]}`,
    backgroundColor: theme.palette.grey[100],
  },
  textButton: {
    fontSize: '1.2rem',
    minWidth: '2rem',
    width: '2rem',
  },
}));

const TraceTimelineHeader = React.memo(
  ({
    startTs,
    endTs,
    isRerooted,
    isRootedTrace,
    onResetRerootButtonClick,
    isSpanDetailOpened,
    onSpanDetailToggle,
    onCollapseButtonClick,
    onExpandButtonClick,
  }) => {
    const classes = useStyles();

    return (
      <Box className={classes.root}>
        <Box display="flex" justifyContent="space-between" mt={1} mr={1} ml={1}>
          <Box display="flex" alignItems="center">
            <ButtonGroup>
              <Button
                className={classes.textButton}
                disabled={!isRootedTrace}
                onClick={onCollapseButtonClick}
              >
                <FontAwesomeIcon icon={faAngleUp} />
              </Button>
              <Button
                className={classes.textButton}
                disabled={!isRootedTrace}
                onClick={onExpandButtonClick}
              >
                <FontAwesomeIcon icon={faAngleDown} />
              </Button>
            </ButtonGroup>
            {isRootedTrace && isRerooted ? (
              <Box ml={1}>
                <Button variant="outlined" onClick={onResetRerootButtonClick}>
                  Reset root
                </Button>
              </Box>
            ) : null}
          </Box>
          <Button className={classes.textButton} onClick={onSpanDetailToggle}>
            <FontAwesomeIcon
              icon={isSpanDetailOpened ? faAngleDoubleRight : faAngleDoubleLeft}
            />
          </Button>
        </Box>
        <TimeMarker startTs={startTs} endTs={endTs} />
      </Box>
    );
  },
);

TraceTimelineHeader.propTypes = propTypes;

export default TraceTimelineHeader;
