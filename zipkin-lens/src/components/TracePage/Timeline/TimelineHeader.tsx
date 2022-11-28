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
  KeyboardArrowUp as KeyboardArrowUpIcon,
} from '@material-ui/icons';
import React from 'react';

const useStyles = makeStyles((theme) => ({
  root: {
    backgroundColor: theme.palette.grey[100],
    borderBottom: `1px solid ${theme.palette.divider}`,
  },
  iconButton: {
    minWidth: 32,
    width: 32,
    height: 32,
  },
}));

type TimelineHeaderProps = {};

export const TimelineHeader = ({}: TimelineHeaderProps) => {
  const classes = useStyles();
  return (
    <Box className={classes.root}>
      <Box px={2} py={1} position="relative">
        <ButtonGroup>
          <Button className={classes.iconButton}>
            <KeyboardArrowUpIcon fontSize="small" />
          </Button>
          <Button className={classes.iconButton}>
            <KeyboardArrowDownIcon fontSize="small" />
          </Button>
        </ButtonGroup>
        <Box position="absolute" right={-14} top={10}>
          <IconButton size="small">
            <KeyboardArrowLeftIcon />
          </IconButton>
        </Box>
      </Box>
    </Box>
  );
};
