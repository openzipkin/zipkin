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

import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  Box,
  Button,
  Theme,
  createStyles,
  makeStyles,
} from '@material-ui/core';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { connect } from 'react-redux';
import { ThunkDispatch } from 'redux-thunk';

import Criterion, { newCriterion } from '../Criterion';
import CriterionBox from './CriterionBox';
import { loadAutocompleteValues } from '../../../slices/autocompleteValuesSlice';
import { loadRemoteServices } from '../../../slices/remoteServicesSlice';
import { loadSpans } from '../../../slices/spansSlice';
import { RootState } from '../../../store';

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
  searchTraces: () => void;
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
  loadRemoteServices: (serviceName: string) => void;
  loadSpans: (serviceName: string) => void;
  loadAutocompleteValues: (autocompleteKey: string) => void;
};

export const SearchBarImpl: React.FC<SearchBarProps> = ({
  searchTraces,
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
  loadRemoteServices,
  loadSpans,
  loadAutocompleteValues,
}) => {
  const classes = useStyles();

  // criterionIndex is the index of the criterion currently being edited.
  // If the value is -1, there is no criterion being edited.
  const [criterionIndex, setCriterionIndex] = useState(-1);

  const handleCriterionFocus = (index: number) => {
    setCriterionIndex(index);
  };

  const handleCriterionChange = (index: number, criterion: Criterion) => {
    const newCriteria = [...criteria];
    newCriteria[index] = criterion;
    onChange(newCriteria);
  };

  const handleCriterionBlur = () => {
    setCriterionIndex(-1);
  };

  const handleCriterionDelete = (index: number) => {
    const newCriteria = criteria.filter((_, i) => i !== index);
    onChange(newCriteria);
    setCriterionIndex(-1);
  };

  const handleCriterionDecide = (index: number) => {
    if (index === criteria.length - 1) {
      const newCriteria = [...criteria];
      newCriteria.push(newCriterion('', ''));
      onChange(newCriteria);
      const nextCriterionIndex = criteria.length;
      setCriterionIndex(nextCriterionIndex);
    } else {
      setCriterionIndex(-1);
    }
  };

  const handleAddButtonClick = useCallback(() => {
    const newCriteria = [...criteria];
    newCriteria.push(newCriterion('', ''));
    onChange(newCriteria);
    const nextCriterionIndex = criteria.length;
    setCriterionIndex(nextCriterionIndex);
  }, [criteria, onChange]);

  const prevServiceName = useRef('');
  useEffect(() => {
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

  // Search for traces if not all criterions are in focus
  // and the Enter key is pressed.
  // Use ref to use the latest criterionIndex state in the callback.
  const isFocusedRef = useRef(false);
  isFocusedRef.current = criterionIndex !== -1;
  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      // Use setTimeout to ensure that the callback is called
      // after the criterionIndex has been updated.
      setTimeout(() => {
        if (!document.activeElement) {
          return;
        }
        if (
          !isFocusedRef.current &&
          document.activeElement.tagName === 'BODY' &&
          event.key === 'Enter'
        ) {
          searchTraces();
        }
      }, 0); // Maybe 0 is enough.
    },
    [searchTraces],
  );
  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [handleKeyDown]);

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
      flexWrap="wrap"
      borderColor="grey.400"
      border={1}
    >
      {criteria.map((criterion, index) => (
        <CriterionBox
          key={criterion.id}
          criteria={criteria}
          criterion={criterion}
          criterionIndex={index}
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
          onFocus={handleCriterionFocus}
          onBlur={handleCriterionBlur}
          onDecide={handleCriterionDecide}
          onChange={handleCriterionChange}
          onDelete={handleCriterionDelete}
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
  loadRemoteServices: (serviceName: string) => {
    dispatch(loadRemoteServices(serviceName));
  },
  loadSpans: (serviceName: string) => {
    dispatch(loadSpans(serviceName));
  },
  loadAutocompleteValues: (autocompleteKey: string) => {
    dispatch(loadAutocompleteValues(autocompleteKey));
  },
});

export default connect(mapStateToProps, mapDispatchToProps)(SearchBarImpl);
