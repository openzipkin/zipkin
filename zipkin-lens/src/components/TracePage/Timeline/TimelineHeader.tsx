/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
  selectedSpan: AdjustedSpan;
  rerootedSpanId: string | undefined;
  setRerootedSpanId: (value: string | undefined) => void;
  absoluteListWidth: number;
  setClosedSpanIdMap: (value: { [spanId: string]: boolean }) => void;
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
  selectedSpan,
  rerootedSpanId,
  setRerootedSpanId,
  absoluteListWidth,
  setClosedSpanIdMap,
}: TimelineHeaderProps) => {
  const classes = useStyles();

  const handleRerootButtonClick = useCallback(() => {
    setRerootedSpanId(selectedSpan.spanId);
  }, [selectedSpan.spanId, setRerootedSpanId]);

  const handleResetRerootButtonClick = useCallback(() => {
    setRerootedSpanId(undefined);
  }, [setRerootedSpanId]);

  const handleCollapseAllButtonClick = useCallback(() => {
    setClosedSpanIdMap(
      spanRows.reduce<{ [spanId: string]: boolean }>((acc, cur) => {
        acc[cur.spanId] = true;
        return acc;
      }, {}),
    );
  }, [setClosedSpanIdMap, spanRows]);

  const handleExpandAllButtonClick = useCallback(() => {
    setClosedSpanIdMap({});
  }, [setClosedSpanIdMap]);

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
          <Tooltip title="Collapse all">
            <Button
              className={classes.iconButton}
              onClick={handleCollapseAllButtonClick}
            >
              <KeyboardArrowUpIcon fontSize="small" />
            </Button>
          </Tooltip>
          <Tooltip title="Expand all">
            <Button
              className={classes.iconButton}
              onClick={handleExpandAllButtonClick}
            >
              <KeyboardArrowDownIcon fontSize="small" />
            </Button>
          </Tooltip>
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
