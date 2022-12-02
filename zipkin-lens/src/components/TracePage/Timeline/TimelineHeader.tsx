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
  makeStyles,
  Tooltip,
} from '@material-ui/core';
import {
  FilterCenterFocus as FilterCenterFocusIcon,
  KeyboardArrowDown as KeyboardArrowDownIcon,
  KeyboardArrowLeft as KeyboardArrowLeftIcon,
  KeyboardArrowRight as KeyboardArrowRightIcon,
  KeyboardArrowUp as KeyboardArrowUpIcon,
  List as ListIcon,
  Visibility as VisibilityIcon,
} from '@material-ui/icons';
import { ToggleButton } from '@material-ui/lab';
import React, { useCallback } from 'react';
import { AdjustedSpan } from '../../../models/AdjustedTrace';
import { TickMarkers } from '../TickMarkers';
import { SpanRow } from '../types';

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
  button: {
    height: 28,
    textTransform: 'none',
  },
  iconButton: {
    minWidth: 0,
    width: 28,
    height: 28,
  },
  tickMarkersWrapper: {
    position: 'absolute',
    left: 120,
    bottom: 0,
    paddingRight: theme.spacing(1),
  },
}));

type TimelineHeaderProps = {
  spanRows: SpanRow[];
  minTimestamp: number;
  selectedMinTimestamp: number;
  selectedMaxTimestamp: number;
  isSpanDetailDrawerOpen: boolean;
  toggleIsSpanDetailDrawerOpen: () => void;
  isMiniTimelineOpen: boolean;
  toggleIsMiniTimelineOpen: () => void;
  isSpanTableOpen: boolean;
  toggleIsSpanTableOpen: () => void;
  selectedSpan: AdjustedSpan;
  rerootedSpanId?: string;
  setRerootedSpanId: (value: string | undefined) => void;
  absoluteListWidth: number;
};

export const TimelineHeader = ({
  spanRows,
  minTimestamp,
  selectedMinTimestamp,
  selectedMaxTimestamp,
  isSpanDetailDrawerOpen,
  toggleIsSpanDetailDrawerOpen,
  isMiniTimelineOpen,
  toggleIsMiniTimelineOpen,
  isSpanTableOpen,
  toggleIsSpanTableOpen,
  selectedSpan,
  rerootedSpanId,
  setRerootedSpanId,
  absoluteListWidth,
}: TimelineHeaderProps) => {
  const classes = useStyles();

  const handleRerootButtonClick = useCallback(() => {
    setRerootedSpanId(selectedSpan.spanId);
  }, [selectedSpan.spanId, setRerootedSpanId]);

  const handleResetRerootButtonClick = useCallback(() => {
    setRerootedSpanId(undefined);
  }, [setRerootedSpanId]);

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
          <Button
            variant="outlined"
            className={classes.button}
            onClick={handleRerootButtonClick}
            startIcon={<FilterCenterFocusIcon fontSize="small" />}
            disabled={
              spanRows.length > 0 && spanRows[0].spanId === selectedSpan.spanId
            }
          >
            Focus on selected span
          </Button>
          <Button
            variant="outlined"
            className={classes.button}
            disabled={!rerootedSpanId}
            onClick={handleResetRerootButtonClick}
          >
            Reset focus
          </Button>
          <Tooltip
            title={
              isMiniTimelineOpen ? 'Close mini timeline' : 'Open mini timeline'
            }
          >
            <ToggleButton
              value="openMiniTimeline"
              className={classes.iconButton}
              selected={isMiniTimelineOpen}
              onClick={toggleIsMiniTimelineOpen}
            >
              <VisibilityIcon fontSize="small" />
            </ToggleButton>
          </Tooltip>
          <Tooltip
            title={isSpanTableOpen ? 'Close span table' : 'Open span table'}
          >
            <ToggleButton
              value="openSpanTable"
              className={classes.iconButton}
              selected={isSpanTableOpen}
              onClick={toggleIsSpanTableOpen}
            >
              <ListIcon fontSize="small" />
            </ToggleButton>
          </Tooltip>
          <Tooltip
            title={
              isSpanDetailDrawerOpen ? 'Close span detail' : 'Open span detail'
            }
          >
            <ToggleButton
              value="openSpanDetailDrawer"
              className={classes.iconButton}
              selected={isSpanDetailDrawerOpen}
              onClick={toggleIsSpanDetailDrawerOpen}
            >
              {isSpanDetailDrawerOpen ? (
                <KeyboardArrowRightIcon fontSize="small" />
              ) : (
                <KeyboardArrowLeftIcon fontSize="small" />
              )}
            </ToggleButton>
          </Tooltip>
        </Box>
      </Box>
      <Box
        className={classes.tickMarkersWrapper}
        right={`calc(100% - ${absoluteListWidth}px)`}
      >
        <TickMarkers
          minTimestamp={selectedMinTimestamp - minTimestamp}
          maxTimestamp={selectedMaxTimestamp - minTimestamp}
        />
      </Box>
    </Box>
  );
};
