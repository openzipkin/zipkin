/*
 * Copyright 2015-2022 The OpenZipkin Authors
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
  ButtonGroup,
  IconButton,
  makeStyles,
} from '@material-ui/core';
import {
  KeyboardArrowDown as KeyboardArrowDownIcon,
  KeyboardArrowLeft as KeyboardArrowLeftIcon,
  KeyboardArrowRight as KeyboardArrowRightIcon,
  KeyboardArrowUp as KeyboardArrowUpIcon,
  SettingsOverscan as SettingsOverscanIcon,
} from '@material-ui/icons';
import { ToggleButton } from '@material-ui/lab';
import React from 'react';
import { TickMarkers } from '../TickMarkers';

const useStyles = makeStyles((theme) => ({
  root: {
    position: 'relative',
    backgroundColor: theme.palette.grey[100],
    borderBottom: `1px solid ${theme.palette.divider}`,
  },
  rightButtonsWrapper: {
    '& > :not(:last-child)': {
      marginRight: theme.spacing(1),
    },
  },
  iconButton: {
    minWidth: 0,
    width: 28,
    height: 28,
  },
  tickMarkersWrapper: {
    position: 'absolute',
    left: 120,
    right: 0,
    bottom: 0,
    paddingRight: theme.spacing(1),
  },
}));

type TimelineHeaderProps = {
  minTimestamp: number;
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
  isSpanDetailDrawerOpen: boolean;
  toggleIsSpanDetailDrawerOpen: () => void;
};

export const TimelineHeader = ({
  minTimestamp,
  selectedMinTimestamp,
  selectedMaxTimestamp,
  isSpanDetailDrawerOpen,
  toggleIsSpanDetailDrawerOpen,
}: TimelineHeaderProps) => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      <Box
        px={2}
        pt={1}
        pb={3}
        position="relative"
        display="flex"
        justifyContent="space-between"
        alignItems="center"
      >
        <ButtonGroup>
          <Button className={classes.iconButton}>
            <KeyboardArrowUpIcon fontSize="small" />
          </Button>
          <Button className={classes.iconButton}>
            <KeyboardArrowDownIcon fontSize="small" />
          </Button>
        </ButtonGroup>
        <Box className={classes.rightButtonsWrapper}>
          <ToggleButton className={classes.iconButton}>
            <SettingsOverscanIcon fontSize="small" />
          </ToggleButton>
          <ToggleButton className={classes.iconButton}>
            <KeyboardArrowUpIcon fontSize="small" />
          </ToggleButton>
          <ToggleButton
            className={classes.iconButton}
            onClick={toggleIsSpanDetailDrawerOpen}
          >
            {isSpanDetailDrawerOpen ? (
              <KeyboardArrowRightIcon fontSize="small" />
            ) : (
              <KeyboardArrowLeftIcon fontSize="small" />
            )}
          </ToggleButton>
        </Box>
      </Box>
      <Box className={classes.tickMarkersWrapper}>
        <TickMarkers
          minTimestamp={selectedMinTimestamp - minTimestamp}
          maxTimestamp={selectedMaxTimestamp - minTimestamp}
        />
      </Box>
    </Box>
  );
};
