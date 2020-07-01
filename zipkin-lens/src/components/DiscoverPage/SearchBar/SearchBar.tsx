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
import React from 'react';
import { ThunkDispatch } from 'redux-thunk';
import { connect } from 'react-redux';
import { Box, Button } from '@material-ui/core';
import { makeStyles, Theme, createStyles } from '@material-ui/core/styles';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faPlus } from '@fortawesome/free-solid-svg-icons';

import CriterionBox from './CriterionBox';
import Criterion from '../Criterion';
import RootState from '../../../types/RootState';
import { fetchServices } from '../../../actions/services-action';
import { fetchSpans } from '../../../actions/spans-action';
import { fetchRemoteServices } from '../../../actions/remote-services-action';
import { fetchAutocompleteValues } from '../../../actions/autocomplete-values-action';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    addButton: {
      height: 40,
      width: 40,
      minWidth: 40,
      color: theme.palette.common.white,
    },
  }),
);

type SearchBarProps = {
  criteria: Criterion[];
  onChange: (criteria: Criterion[]) => void;
  serviceNames: string[];
  isLoadingServiceNames: boolean;
  spanNames: string[];
  isLoadingSpanNames: boolean;
  remoteServiceNames: string[];
  isLoadingRemoteServiceNames: boolean;
  autocompleteKeys: string[];
  autocompleteValues: string[];
  isLoadingAutocompleteValues: boolean;
  loadServices: () => void;
  loadRemoteServices: (serviceName: string) => void;
  loadSpans: (serviceName: string) => void;
  loadAutocompleteValues: (autocompleteKey: string) => void;
};

export const SearchBarImpl: React.FC<SearchBarProps> = ({
  criteria,
  onChange,
  serviceNames,
  isLoadingServiceNames,
  spanNames,
  isLoadingSpanNames,
  remoteServiceNames,
  isLoadingRemoteServiceNames,
  autocompleteKeys,
  autocompleteValues,
  isLoadingAutocompleteValues,
  loadServices,
  loadRemoteServices,
  loadSpans,
  loadAutocompleteValues,
}) => {
  const classes = useStyles();

  // criterionIndex is the index of the criterion currently being edited.
  // If the value is -1, there is no criterion being edited.
  const [criterionIndex, setCriterionIndex] = React.useState(-1);

  const handleCriterionFocus = (index: number) => () => {
    setCriterionIndex(index);
  };

  const handleCriterionChange = (index: number) => (criterion: Criterion) => {
    const newCriteria = [...criteria];
    newCriteria[index] = criterion;
    onChange(newCriteria);
  };

  const handleCriterionBlur = () => () => {
    setCriterionIndex(-1);
  };

  const handleCriterionDelete = (index: number) => () => {
    const newCriteria = criteria.filter((_, i) => i !== index);
    onChange(newCriteria);
  };

  const handleCriterionDecide = (index: number) => () => {
    if (index === criteria.length - 1) {
      const newCriteria = [...criteria];
      newCriteria.push({ key: '', value: '' });
      onChange(newCriteria);
      const nextCriterionIndex = criteria.length;
      setCriterionIndex(nextCriterionIndex);
    } else {
      setCriterionIndex(-1);
    }
  };

  const handleAddButtonClick = React.useCallback(() => {
    const newCriteria = [...criteria];
    newCriteria.push({ key: '', value: '' });
    onChange(newCriteria);
    const nextCriterionIndex = criteria.length;
    setCriterionIndex(nextCriterionIndex);
  }, [criteria, onChange]);

  React.useEffect(() => {
    loadServices();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const prevServiceName = React.useRef('');
  React.useEffect(() => {
    const criterion = criteria.find(
      // eslint-disable-next-line no-shadow
      (criterion) => criterion.key === 'serviceName',
    );
    const serviceName = criterion ? criterion.value : '';
    if (serviceName !== prevServiceName.current) {
      prevServiceName.current = serviceName;
      loadSpans(serviceName);
      loadRemoteServices(serviceName);
    }
  }, [criteria, loadSpans, loadRemoteServices]);

  return (
    <Box
      minHeight={60}
      display="flex"
      alignItems="center"
      pr={2}
      pl={2}
      pt={1}
      pb={1}
      borderRadius={3}
      bgcolor="background.paper"
      boxShadow={3}
      flexWrap="wrap"
    >
      {criteria.map((criterion, index) => (
        <CriterionBox
          key={index}
          criteria={criteria}
          criterion={criterion}
          serviceNames={serviceNames}
          remoteServiceNames={remoteServiceNames}
          spanNames={spanNames}
          autocompleteKeys={autocompleteKeys}
          autocompleteValues={autocompleteValues}
          isLoadingServiceNames={isLoadingServiceNames}
          isLoadingRemoteServiceNames={isLoadingRemoteServiceNames}
          isLoadingSpanNames={isLoadingSpanNames}
          isLoadingAutocompleteValues={isLoadingAutocompleteValues}
          isFocused={index === criterionIndex}
          onFocus={handleCriterionFocus(index)}
          onBlur={handleCriterionBlur()}
          onDecide={handleCriterionDecide(index)}
          onChange={handleCriterionChange(index)}
          onDelete={handleCriterionDelete(index)}
          loadAutocompleteValues={loadAutocompleteValues}
        />
      ))}
      <Button
        color="secondary"
        variant="contained"
        onClick={handleAddButtonClick}
        className={classes.addButton}
        data-testid="add-button"
      >
        <FontAwesomeIcon icon={faPlus} size="lg" />
      </Button>
    </Box>
  );
};

// For unit testing, `connect` is easier to use than
// useSelector or useDispatch hooks.
const mapStateToProps = (state: RootState) => ({
  serviceNames: state.services.services,
  isLoadingServiceNames: state.services.isLoading,
  spanNames: state.spans.spans,
  isLoadingSpanNames: state.spans.isLoading,
  remoteServiceNames: state.remoteServices.remoteServices,
  isLoadingRemoteServiceNames: state.remoteServices.isLoading,
  autocompleteKeys: state.autocompleteKeys.autocompleteKeys,
  autocompleteValues: state.autocompleteValues.autocompleteValues,
  isLoadingAutocompleteValues: state.autocompleteValues.isLoading,
});

// TODO: Give the appropriate type to ThunkDispatch after TypeScriptizing all action creators.
const mapDispatchToProps = (
  dispatch: ThunkDispatch<RootState, undefined, any>,
) => ({
  loadServices: () => {
    dispatch(fetchServices());
  },
  loadRemoteServices: (serviceName: string) => {
    dispatch(fetchRemoteServices(serviceName));
  },
  loadSpans: (serviceName: string) => {
    dispatch(fetchSpans(serviceName));
  },
  loadAutocompleteValues: (autocompleteKey: string) => {
    dispatch(fetchAutocompleteValues(autocompleteKey));
  },
});

export default connect(mapStateToProps, mapDispatchToProps)(SearchBarImpl);
