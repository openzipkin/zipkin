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
import { useDispatch, useSelector } from 'react-redux';
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
import {fetchAutocompleteKeys} from "../../../actions/autocomplete-keys-action";

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
};

const SearchBar: React.FC<SearchBarProps> = ({ criteria, onChange }) => {
  const classes = useStyles();

  const inputEls = React.useRef<HTMLInputElement[]>([]);
  const setInputEl = (index: number) => (el: HTMLInputElement) => {
    inputEls.current[index] = el;
  };

  // criterionIndex is the index of the criterion currently being edited.
  // If the value is -1, there is no criterion being edited.
  const [criterionIndex, setCriterionIndex] = React.useState(-1);

  const handleCriterionFocus = (index: number) => () => {
    setCriterionIndex(index);
    // Use setTimeout to blur from the old criterion and then
    // focus on the new criterion.
    setTimeout(() => {
      inputEls.current[index].focus();
    }, 0);
  };

  const handleCriterionChange = (index: number) => (criterion: Criterion) => {
    const newCriteria = [...criteria];
    newCriteria[index] = criterion;
    onChange(newCriteria);
  };

  const handleCriterionBlur = (index: number) => () => {
    setCriterionIndex(-1);
    inputEls.current[index].blur();
  };

  const handleCriterionDelete = (index: number) => () => {
    const newCriteria = criteria.filter((_, i) => i !== index);
    onChange(newCriteria);
  };

  const handleAddButtonClick = React.useCallback(() => {
    const newCriteria = [...criteria];
    newCriteria.push({ key: '', value: '' });
    onChange(newCriteria);
    const nextCriterionIndex = criteria.length;
    setCriterionIndex(nextCriterionIndex);
    setTimeout(() => {
      inputEls.current[nextCriterionIndex].focus();
    }, 0);
  }, [criteria, onChange]);

  const [
    serviceNames,
    isLoadingServiceNames,
  ] = useSelector((state: RootState) => [
    state.services.services,
    state.services.isLoading,
  ]);

  const [spanNames, isLoadingSpanNames] = useSelector((state: RootState) => [
    state.spans.spans,
    state.spans.isLoading,
  ]);

  const [
    remoteServiceNames,
    isLoadingRemoteServiceNames,
  ] = useSelector((state: RootState) => [
    state.remoteServices.remoteServices,
    state.remoteServices.isLoading,
  ]);

  const [
    autocompleteKeys,
    isLoadingAutocompleteKeys,
  ] = useSelector((state: RootState) => [
    state.autocompleteKeys.autocompleteKeys,
    state.autocompleteKeys.isLoading,
  ]);

  const dispatch = useDispatch();

  React.useEffect(() => {
    dispatch(fetchServices());
    dispatch(fetchAutocompleteKeys());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const prevServiceName = React.useRef('');
  React.useEffect(() => {
    const criterion = criteria.find(
      // eslint-disable-next-line no-shadow
      (criterion) => criterion.key === 'serviceName',
    );
    const serviceName = criterion ? criterion.key : '';
    if (serviceName !== prevServiceName.current) {
      prevServiceName.current = serviceName;
      dispatch(fetchSpans(serviceName));
      dispatch(fetchRemoteServices(serviceName));
    }
  }, [criteria, dispatch]);

  const [
    autocompleteValues,
    isLoadingAutocompleteValues,
  ] = useSelector((state: RootState) => [
    state.autocompleteValues.autocompleteValues,
    state.autocompleteValues.isLoading,
  ]);

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
          setInputEl={setInputEl(index)}
          criterion={criterion}
          serviceNames={serviceNames}
          remoteServiceNames={remoteServiceNames}
          spanNames={spanNames}
          autocompleteKeys={autocompleteKeys}
          autocompleteValues={autocompleteValues}
          isLoadingServiceNames={isLoadingServiceNames}
          isLoadingRemoteServiceNames={isLoadingRemoteServiceNames}
          isLoadingSpanNames={isLoadingSpanNames}
          isLoadingAutocompleteKeys={isLoadingAutocompleteKeys}
          isLoadingAutocompleteValues={isLoadingAutocompleteValues}
          isFocused={index === criterionIndex}
          onFocus={handleCriterionFocus(index)}
          onBlur={handleCriterionBlur(index)}
          onChange={handleCriterionChange(index)}
          onDelete={handleCriterionDelete(index)}
        />
      ))}
      <Button
        color="secondary"
        variant="contained"
        onClick={handleAddButtonClick}
        className={classes.addButton}
      >
        <FontAwesomeIcon icon={faPlus} size="lg" />
      </Button>
    </Box>
  );
};

export default SearchBar;
