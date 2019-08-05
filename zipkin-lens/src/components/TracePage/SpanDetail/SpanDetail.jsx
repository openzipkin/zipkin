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
import React from 'react';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Typography from '@material-ui/core/Typography';
import grey from '@material-ui/core/colors/grey';

import SpanAnnotation from './SpanAnnotation';
import { detailedSpanPropTypes } from '../../../prop-types';
import SpanTags from './SpanTags';

const propTypes = {
  span: detailedSpanPropTypes.isRequired,
};

const useStyles = makeStyles(theme => ({
  root: {
    backgroundColor: grey[100],
  },
  serviceName: {
    textTransform: 'uppercase',
  },
  spanName: {
    color: theme.palette.text.hint,
  },
}));

const SpanDetail = ({ span }) => {
  const classes = useStyles();

  return (
    <Box
      width="100%"
      height="100%"
      borderLeft={1}
      borderColor="grey.300"
      className={classes.root}
    >
      <Box>
        <Box
          pt={2}
          pl={2}
          pr={2}
          pb={1.5}
          borderBottom={1}
          borderColor="grey.300"
        >
          <Typography variant="h5" className={classes.serviceName}>
            {span.serviceName}
          </Typography>
          <Typography variant="h6" className={classes.spanName}>
            {span.spanName}
          </Typography>
        </Box>
        <Box
          pt={1}
          pl={2}
          pr={2}
          pb={1.5}
        >
          <Box fontWeight="bold" fontSize="1.4rem">
            Annotations
          </Box>
          {
            span.annotations.map(annotation => (
              <Box mt={1} key={annotation.value}>
                <SpanAnnotation key={annotation.value} annotation={annotation} />
              </Box>
            ))
          }
        </Box>
        <Box
          pt={1}
          pl={2}
          pr={2}
          pb={1.5}
        >
          <Box fontWeight="bold" fontSize="1.4rem" mb={0.5}>
            Tags
          </Box>
          <SpanTags tags={span.tags} />
        </Box>
      </Box>
    </Box>
  );
};

SpanDetail.propTypes = propTypes;

export default SpanDetail;
