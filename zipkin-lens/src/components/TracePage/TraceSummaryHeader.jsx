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
import React, { useCallback } from 'react';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';

import TraceIdSearchInput from '../Common/TraceIdSearchInput';
import TraceJsonUploader from '../Common/TraceJsonUploader';
import { detailedTraceSummaryPropTypes } from '../../prop-types';
import * as api from '../../constants/api';

const propTypes = {
  traceSummary: detailedTraceSummaryPropTypes,
  rootSpanIndex: PropTypes.number,
};

const defaultProps = {
  traceSummary: null,
  rootSpanIndex: 0,
};

const useStyles = makeStyles(theme => ({
  root: {
    paddingLeft: theme.spacing(3),
    paddingRight: theme.spacing(3),
  },
  upperBox: {
    width: '100%',
    display: 'flex',
    justifyContent: 'space-between',
    borderBottom: `1px solid ${theme.palette.grey[300]}`,
  },
  serviceNameAndSpanName: {
    display: 'flex',
    alignItems: 'center',
  },
  serviceName: {
    textTransform: 'uppercase',
  },
  spanName: {
    color: theme.palette.text.secondary,
  },
  jsonUploaderAndSearchInput: {
    display: 'flex',
    alignItems: 'center',
    paddingRight: theme.spacing(4),
  },
  lowerBox: {
    display: 'flex',
    justifyContent: 'space-between',
    marginTop: theme.spacing(0.5),
    marginBottom: theme.spacing(0.5),
  },
  traceInfo: {
    display: 'flex',
    alignItems: 'center',
  },
  traceInfoEntry: {
    marginRight: theme.spacing(1),
    display: 'flex',
  },
  traceInfoLabel: {
    fontWeight: 'bold',
    color: theme.palette.grey[600],
  },
  traceInfoValue: {
    fontWeight: 'bold',
    marginLeft: theme.spacing(0.8),
  },
  saveButton: {
    fontSize: '0.5rem',
    lineHeight: 0.8,
  },
  saveButtonIcon: {
    marginRight: theme.spacing(1),
  },
}));

const TraceSummaryHeader = React.memo(({ traceSummary, rootSpanIndex }) => {
  const classes = useStyles();

  const handleSaveButtonClick = useCallback(() => {
    if (!traceSummary || !traceSummary.traceId) {
      return;
    }
    fetch(`${api.TRACE}/${traceSummary.traceId}`)
      .then(resp => resp.blob())
      .then((blob) => {
        const a = document.createElement('a');
        a.href = window.URL.createObjectURL(blob);
        a.download = `${traceSummary.traceId}.json`;
        a.click();
        // See: https://stackoverflow.com/questions/30694453/blob-createobjecturl-download-not-working-in-firefox-but-works-when-debugging
        setTimeout(() => {
          window.URL.revokeObjectURL(a.href);
        }, 100);
      });
  }, [traceSummary]);

  const traceInfo = traceSummary ? (
    <Box className={classes.traceInfo}>
      {
        [
          { label: 'Duration', value: traceSummary.durationStr },
          { label: 'Services', value: traceSummary.serviceNameAndSpanCounts.length },
          { label: 'Depth', value: traceSummary.depth },
          { label: 'Total Spans', value: traceSummary.spans.length },
          {
            label: 'Trace ID',
            value: rootSpanIndex === 0
              ? traceSummary.traceId
              : `${traceSummary.traceId} - ${traceSummary.spans[rootSpanIndex].spanId}`,
          },
        ].map(entry => (
          <Box key={entry.label} className={classes.traceInfoEntry}>
            <Box className={classes.traceInfoLabel}>
              {`${entry.label}:`}
            </Box>
            <Box className={classes.traceInfoValue}>
              {entry.value}
            </Box>
          </Box>
        ))
      }
    </Box>
  ) : <div />;

  return (
    <Box className={classes.root}>
      <Box className={classes.upperBox}>
        <Box className={classes.serviceNameAndSpanName}>
          {
            traceSummary ? (
              <>
                <Typography variant="h5" className={classes.serviceName}>
                  {traceSummary.rootSpan.serviceName}
                </Typography>
                <Typography variant="h5" className={classes.spanName}>
                  {` : ${traceSummary.rootSpan.spanName}`}
                </Typography>
              </>
            ) : null
          }
        </Box>
        <Box className={classes.jsonUploaderAndSearchInput}>
          <TraceJsonUploader />
          <TraceIdSearchInput />
        </Box>
      </Box>
      <Box className={classes.lowerBox}>
        {traceInfo}
        <Button variant="outlined" className={classes.saveButton} onClick={handleSaveButtonClick}>
          <FontAwesomeIcon icon={faDownload} className={classes.saveButtonIcon} />
          Download JSON
        </Button>
      </Box>
    </Box>
  );
});

TraceSummaryHeader.propTypes = propTypes;
TraceSummaryHeader.defaultProps = defaultProps;

export default TraceSummaryHeader;
