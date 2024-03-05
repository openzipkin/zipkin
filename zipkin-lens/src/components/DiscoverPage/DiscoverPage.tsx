/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Box, CircularProgress, Typography } from '@material-ui/core';
import React, { useEffect } from 'react';
import { connect } from 'react-redux';
import { ThunkDispatch } from 'redux-thunk';

import { Trans, useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();

  useEffect(() => {
    loadAutocompleteKeys();
    loadServices();
  }, []);

  if (!config.searchEnabled) {
    return (
      <Typography variant="body1">
        <Trans t={t}>
          Searching has been disabled via the searchEnabled property. You can
          still view specific traces of which you know the trace id by entering
          it in the &quot;Trace ID...&quot; textbox on the top-right.
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
