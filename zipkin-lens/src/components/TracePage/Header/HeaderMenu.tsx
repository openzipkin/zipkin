/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Button, makeStyles, Menu, MenuItem } from '@material-ui/core';
import { Menu as MenuIcon } from '@material-ui/icons';
import React, { useCallback } from 'react';
import { useDispatch } from 'react-redux';
import { Trans, useTranslation } from 'react-i18next';
import * as api from '../../../constants/api';
import AdjustedTrace from '../../../models/AdjustedTrace';
import Span from '../../../models/Span';
import { setAlert } from '../../App/slice';
import { useUiConfig } from '../../UiConfig';

const useStyles = makeStyles(() => ({
  iconButton: {
    minWidth: 32,
    width: 32,
    height: 32,
  },
}));

type HeaderMenuProps = {
  trace: AdjustedTrace;
  rawTrace: Span[];
};

export const HeaderMenu = ({ trace, rawTrace }: HeaderMenuProps) => {
  const classes = useStyles();
  const config = useUiConfig();
  const dispatch = useDispatch();
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const { t } = useTranslation();

  const logsUrl = config.logsUrl
    ? config.logsUrl.replace(/{traceId}/g, trace.traceId)
    : undefined;
  const traceJsonUrl = `${api.TRACE}/${trace.traceId}`;
  const archivePostUrl = config.archivePostUrl
    ? config.archivePostUrl
    : undefined;
  const archiveUrl = config.archiveUrl
    ? config.archiveUrl.replace('{traceId}', trace.traceId)
    : undefined;

  const handleMenuButtonClick = (
    event: React.MouseEvent<HTMLButtonElement>,
  ) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleArchiveButtonClick = useCallback(() => {
    // We don't store the raw json in the browser yet, so we need to make an
    // HTTP call to retrieve it again.
    fetch(`${api.TRACE}/${trace.traceId}`)
      .then((response) => {
        if (!response.ok) {
          throw new Error('Failed to fetch trace from backend');
        }
        return response.json();
      })
      .then((json) => {
        // Add zipkin.archived tag to root span
        /* eslint-disable-next-line no-restricted-syntax */
        for (const span of json) {
          if ('parentId' in span === false) {
            const tags = span.tags || {};
            tags['zipkin.archived'] = 'true';
            span.tags = tags;
            break;
          }
        }
        return json;
      })
      .then((json) => {
        return fetch(archivePostUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(json),
        });
      })
      .then((response) => {
        if (
          !response.ok ||
          (response.status !== 202 && response.status === 200)
        ) {
          throw new Error('Failed to archive the trace');
        }
        if (archiveUrl) {
          dispatch(
            setAlert({
              message: `Archive successful! This trace is now accessible at ${archiveUrl}`,
              severity: 'success',
            }),
          );
        } else {
          dispatch(
            setAlert({
              message: `Archive successful!`,
              severity: 'success',
            }),
          );
        }
      })
      .catch(() => {
        dispatch(
          setAlert({
            message: 'Failed to archive the trace',
            severity: 'error',
          }),
        );
      });
  }, [archivePostUrl, archiveUrl, dispatch, trace.traceId]);

  const handleDownloadJsonButtonClick = useCallback(() => {
    const url = window.URL.createObjectURL(
      new Blob([JSON.stringify(rawTrace)], { type: 'text/json' }),
    );
    const a = document.createElement('a');
    a.download = `${trace.traceId}.json`;
    a.href = url;
    document.body.appendChild(a);
    a.click();
    a.parentNode?.removeChild(a);

    handleMenuClose();
  }, [rawTrace, trace.traceId]);

  return (
    <>
      <Button
        variant="outlined"
        className={classes.iconButton}
        onClick={handleMenuButtonClick}
      >
        <MenuIcon fontSize="small" />
      </Button>
      <Menu
        anchorEl={anchorEl}
        keepMounted
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        {traceJsonUrl && (
          <MenuItem onClick={handleDownloadJsonButtonClick}>
            <Trans t={t}>Download JSON</Trans>
          </MenuItem>
        )}
        {logsUrl && (
          <MenuItem
            onClick={handleMenuClose}
            component="a"
            href={logsUrl}
            target="_blank"
            rel="noopener"
          >
            <Trans t={t}>View Logs</Trans>
          </MenuItem>
        )}
        {archivePostUrl && (
          <MenuItem onClick={handleArchiveButtonClick}>
            <Trans t={t}>Archive Trace</Trans>
          </MenuItem>
        )}
      </Menu>
    </>
  );
};
