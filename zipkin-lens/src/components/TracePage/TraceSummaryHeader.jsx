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
import Typography from '@material-ui/core/Typography';

import TraceIdSearchInput from '../Common/TraceIdSearchInput';
import TraceJsonUploader from '../Common/TraceJsonUploader';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  traceSummary: detailedTraceSummaryPropTypes,
  rootSpanIndex: PropTypes.number.isRequired,
};

const defaultProps = {
  traceSummary: null,
};

const useStyles = makeStyles(theme => ({
  headerTop: {
    borderColor: theme.palette.grey[300],
  },
  serviceName: {
    textTransform: 'uppercase',
  },
  spanName: {
    color: theme.palette.text.hint,
  },
}));

const TraceSummaryHeader = React.memo(({ traceSummary, rootSpanIndex }) => {
  const classes = useStyles();

  return (
    <Box pl={3} pr={3}>
      <Box
        width="100%"
        display="flex"
        justifyContent="space-between"
        borderBottom={1}
        className={classes.headerTop}
      >
        <Box display="flex" alignItems="center">
          {
            traceSummary ? (
              <>
                <Box className={classes.serviceName}>
                  <Typography variant="h5">
                    {traceSummary.rootSpan.serviceName}
                  </Typography>
                </Box>
                <Box className={classes.spanName}>
                  <Typography variant="h5">
                    {`: ${traceSummary.rootSpan.spanName}`}
                  </Typography>
                </Box>
              </>
            ) : null
          }
        </Box>
        <Box pr={4} display="flex" alignItems="center">
          <TraceJsonUploader />
          <TraceIdSearchInput />
        </Box>
      </Box>
      <Box
        display="flex"
        mt={0.5}
        mb={0.5}
        alignItems="center"
        fontSize="1rem"
      >
        {
          traceSummary ? (
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
            ].map(e => (
              <Box key={e.label} mr={1} display="flex">
                <Box fontWeight="bold" color="grey.600">
                  {`${e.label}:`}
                </Box>
                <Box fontWeight="bold" ml={0.8}>
                  {e.value}
                </Box>
              </Box>
            ))
          ) : null
        }
      </Box>
    </Box>
  );
});

TraceSummaryHeader.propTypes = propTypes;
TraceSummaryHeader.defaultProps = defaultProps;

export default TraceSummaryHeader;
