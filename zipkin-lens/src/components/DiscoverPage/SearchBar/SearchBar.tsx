/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
import { loadAutocompleteValues } from '../../../slices/autocompleteValuesSlice';
import { loadRemoteServices } from '../../../slices/remoteServicesSlice';
import { loadSpans } from '../../../slices/spansSlice';
import { RootState } from '../../../store';
import Criterion, { newCriterion } from '../Criterion';
import { CRITERION_BOX_HEIGHT } from './constants';
import CriterionBox from './CriterionBox';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    root: {
      display: 'flex',
      gap: `${theme.spacing(1)}px`,
      alignItems: 'center',
      padding: theme.spacing(1),
      borderRadius: 3,
      backgroundColor: theme.palette.background.paper,
      flexWrap: 'wrap',
      border: `1px solid ${theme.palette.divider}`,
    },
    addButton: {
      height: CRITERION_BOX_HEIGHT,
      width: CRITERION_BOX_HEIGHT,
      minWidth: 0,
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
    <Box className={classes.root}>
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
