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

/* eslint-disable no-shadow */

import { Trans } from '@lingui/macro';
import { Box, CircularProgress, Typography } from '@material-ui/core';
import React, { useEffect } from 'react';
import { connect } from 'react-redux';
import { ThunkDispatch } from 'redux-thunk';

import DiscoverPageContent from './DiscoverPageContent';
import TraceIdSearchInput from '../Common/TraceIdSearchInput';
import TraceJsonUploader from '../Common/TraceJsonUploader';
import { useUiConfig } from '../UiConfig';
import { fetchAutocompleteKeys } from '../../actions/autocomplete-keys-action';
import RootState from '../../types/RootState';

interface DiscoverPageImplProps {
  autocompleteKeys: string[];
  isLoadingAutocompleteKeys: boolean;
  loadAutocompleteKeys: () => void;
}

const DiscoverPageImpl: React.FC<DiscoverPageImplProps> = ({
  autocompleteKeys,
  isLoadingAutocompleteKeys,
  loadAutocompleteKeys,
}) => {
  const config = useUiConfig();

  let content: JSX.Element;

  useEffect(() => {
    loadAutocompleteKeys();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!config.searchEnabled) {
    content = (
      <Typography variant="body1">
        <Trans>
          Searching has been disabled via the searchEnabled property. You can
          still view specific traces of which you know the trace id by entering
          it in the "trace id..." textbox on the top-right.
        </Trans>
      </Typography>
    );
  } else if (isLoadingAutocompleteKeys) {
    // Need to fetch autocompleteKeys before displaying a search bar,
    // because SearchBar uses autocompleteKeys inside.
    content = (
      <Box
        height="100%"
        width="100%"
        display="flex"
        alignItems="center"
        justifyContent="center"
      >
        <CircularProgress />
      </Box>
    );
  } else {
    content = <DiscoverPageContent autocompleteKeys={autocompleteKeys} />;
  }

  return (
    <Box width="100%" height="100vh" display="flex" flexDirection="column">
      <Box
        pl={3}
        pr={3}
        display="flex"
        justifyContent="space-between"
        alignItems="center"
      >
        <Typography variant="h5">
          <Trans>Discover</Trans>
        </Typography>
        <Box pr={3} display="flex" alignItems="center">
          <TraceJsonUploader />
          <TraceIdSearchInput />
        </Box>
      </Box>
      {content}
    </Box>
  );
};

// For unit testing, `connect` is easier to use than
// `useSelector` or `useDispatch` hooks.
const mapStateToProps = (state: RootState) => ({
  autocompleteKeys: state.autocompleteKeys.autocompleteKeys,
  isLoadingAutocompleteKeys: state.autocompleteKeys.isLoading,
});

const mapDispatchToProps = (
  dispatch: ThunkDispatch<RootState, undefined, any>,
) => ({
  loadAutocompleteKeys: () => {
    dispatch(fetchAutocompleteKeys());
  },
});

export default connect(mapStateToProps, mapDispatchToProps)(DiscoverPageImpl);
