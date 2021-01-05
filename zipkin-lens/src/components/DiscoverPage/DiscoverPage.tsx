/*
 * Copyright 2015-2021 The OpenZipkin Authors
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
import { useUiConfig } from '../UiConfig';
import { loadAutocompleteKeys } from '../../slices/autocompleteKeysSlice';
import { loadServices } from '../../slices/servicesSlice';
import { RootState } from '../../store';

interface DiscoverPageImplProps {
  autocompleteKeys: string[];
  isLoadingAutocompleteKeys: boolean;
  isLoadingServices: boolean;
  loadAutocompleteKeys: () => void;
  loadServices: () => void;
}

const DiscoverPageImpl: React.FC<DiscoverPageImplProps> = ({
  autocompleteKeys,
  isLoadingAutocompleteKeys,
  isLoadingServices,
  loadAutocompleteKeys,
  loadServices,
}) => {
  const config = useUiConfig();

  useEffect(() => {
    loadAutocompleteKeys();
    loadServices();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!config.searchEnabled) {
    return (
      <Typography variant="body1">
        <Trans>
          Searching has been disabled via the searchEnabled property. You can
          still view specific traces of which you know the trace id by entering
          it in the "Trace ID..." textbox on the top-right.
        </Trans>
      </Typography>
    );
  }

  if (isLoadingAutocompleteKeys || isLoadingServices) {
    // Need to fetch autocompleteKeys before displaying a search bar,
    // because SearchBar uses autocompleteKeys inside.
    return (
      <Box
        height="100vh"
        width="100%"
        top={0}
        position="fixed"
        display="flex"
        alignItems="center"
        justifyContent="center"
      >
        <CircularProgress />
      </Box>
    );
  }

  return <DiscoverPageContent autocompleteKeys={autocompleteKeys} />;
};

// For unit testing, `connect` is easier to use than
// `useSelector` or `useDispatch` hooks.
const mapStateToProps = (state: RootState) => ({
  autocompleteKeys: state.autocompleteKeys.autocompleteKeys,
  isLoadingAutocompleteKeys: state.autocompleteKeys.isLoading,
  isLoadingServices: state.services.isLoading,
});

const mapDispatchToProps = (
  dispatch: ThunkDispatch<RootState, undefined, any>,
) => ({
  loadAutocompleteKeys: () => {
    dispatch(loadAutocompleteKeys());
  },
  loadServices: () => {
    dispatch(loadServices());
  },
});

export default connect(mapStateToProps, mapDispatchToProps)(DiscoverPageImpl);
