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

import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Box, ClickAwayListener } from '@material-ui/core';
import React, {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useMount } from 'react-use';
import styled, { keyframes } from 'styled-components';

import HowToUse from './HowToUse';
import SuggestionList from './SuggestionList';
import Criterion, { newCriterion } from '../Criterion';

const fadeIn = keyframes`
  0% { opacity: 0 }
  30% { opacity: 0.1 }
  70% { opacity: 0.9 }
  100% { opacity: 1 }
`;

const Root = styled(Box)`
  display: flex;
  height: 40px;
  border-radius: 3px;
  box-shadow: ${({ theme }) => theme.shadows[1]};
  overflow: hidden;
  margin-right: ${({ theme }) => theme.spacing(1)}px;
  font-size: 1.1rem;
  color: ${({ theme }) => theme.palette.common.white};
  cursor: pointer;
  & > *:hover {
    opacity: 0.9;
  }
  animation: 0.25s 0s both ${fadeIn};
`;

const FocusedRoot = styled(Box)`
  margin-right: ${({ theme }) => theme.spacing(2)}px;
  position: relative;
  animation: 0.25s 0s both ${fadeIn};
  z-index: ${({ theme }) => theme.zIndex.modal};
`;

const DeleteButton = styled.button`
  height: 100%;
  width: 30;
  color: ${({ theme }) => theme.palette.common.white};
  background-color: ${({ theme }) => theme.palette.primary.main};
  cursor: pointer;
  border: none;
  &:focus {
    outline: none;
  }
`;

const Input = styled.input.attrs(() => ({
  'data-testid': 'criterion-input',
}))`
  width: 350px;
  height: 40px;
  padding: 10px;
  box-sizing: border-box;
  font-size: 1.1rem;
`;

interface CriterionBoxProps {
  criteria: Criterion[];
  criterion: Criterion;
  criterionIndex: number;
  serviceNames: string[];
  remoteServiceNames: string[];
  spanNames: string[];
  autocompleteKeys: string[];
  autocompleteValues: string[];
  isLoadingServiceNames: boolean;
  isLoadingRemoteServiceNames: boolean;
  isLoadingSpanNames: boolean;
  isLoadingAutocompleteValues: boolean;
  isFocused: boolean;
  onFocus: (index: number) => void;
  onBlur: () => void;
  onDecide: (index: number) => void;
  onChange: (index: number, criterion: Criterion) => void;
  onDelete: (index: number) => void;
  loadAutocompleteValues: (autocompleteKey: string) => void;
}

const initialText = (criterion: Criterion) => {
  if (criterion.key) {
    if (criterion.value) {
      return `${criterion.key}=${criterion.value}`;
    }
    return `${criterion.key}=`;
  }
  return '';
};

const CriterionBox: React.FC<CriterionBoxProps> = ({
  criteria,
  criterion,
  criterionIndex,
  serviceNames,
  remoteServiceNames,
  spanNames,
  autocompleteKeys,
  autocompleteValues,
  isLoadingServiceNames,
  isLoadingRemoteServiceNames,
  isLoadingSpanNames,
  isLoadingAutocompleteValues,
  isFocused,
  onFocus,
  onBlur,
  onDecide,
  onChange,
  onDelete,
  loadAutocompleteValues,
}) => {
  const inputEl = useRef<HTMLInputElement>(null);

  const [text, setText] = useState(initialText(criterion));
  const [fixedText, setFixedText] = useState(initialText(criterion));

  useMount(() => {
    if (inputEl.current) {
      inputEl.current.focus();
    }
  });

  const prevIsFocused = useRef(isFocused);
  useLayoutEffect(() => {
    if (prevIsFocused.current && !isFocused) {
      if (!fixedText) {
        onDelete(criterionIndex);
        return;
      }
      let strs = fixedText.split('=');

      if (strs.length === 1 || strs[1] === '') {
        switch (strs[0]) {
          // If the value does not exist in these conditions, delete this criterion.
          case 'serviceName':
          case 'spanName':
          case 'remoteServiceName':
          case 'maxDuration':
          case 'minDuration':
          case 'tagQuery':
            setFixedText('');
            onDelete(criterionIndex);
            return;
          default:
            break;
        }
      }

      // If the length is greater than 2, there is more than one "=" in the text.
      // Service names, span names, and tag's keys and values can contain '=',
      // so this is also valid.
      // In this case, treat the first "=" as a separator between key and value.
      if (strs.length > 2) {
        strs = fixedText.split(/=(.+)/);
      }
      onChange(criterionIndex, newCriterion(strs[0], strs[1] || ''));
    } else if (!prevIsFocused.current && isFocused) {
      if (inputEl.current) {
        inputEl.current.focus();
      }
    }
    prevIsFocused.current = isFocused;
  }, [isFocused, fixedText, onChange, onDelete, criterionIndex]);

  const [keyText, valueText] = useMemo(() => {
    const ss = fixedText.split('=');
    return [ss[0], ss[1] || ''];
  }, [fixedText]);

  const isEnteringKey = !text.includes('=');
  const isLoadingSuggestions = useMemo(() => {
    if (isEnteringKey) {
      return false;
    }
    switch (keyText) {
      case 'serviceName':
        return isLoadingServiceNames;
      case 'spanName':
        return isLoadingSpanNames;
      case 'remoteServiceName':
        return isLoadingRemoteServiceNames;
      default:
        if (autocompleteKeys.includes(keyText)) {
          return isLoadingAutocompleteValues;
        }
    }
    return false;
  }, [
    keyText,
    isEnteringKey,
    isLoadingServiceNames,
    isLoadingSpanNames,
    isLoadingRemoteServiceNames,
    isLoadingAutocompleteValues,
    autocompleteKeys,
  ]);

  const suggestions = useMemo(() => {
    if (isEnteringKey) {
      let keys;

      // spanName and remoteServiceName are fetched after serviceName is
      // selected, so so they will not be displayed until serviceName is selected.
      if (criteria.find(({ key }) => key === 'serviceName')) {
        keys = [
          'serviceName',
          'spanName',
          'remoteServiceName',
          'maxDuration',
          'minDuration',
          'tagQuery',
          ...autocompleteKeys,
        ];
      } else {
        keys = [
          'serviceName',
          'maxDuration',
          'minDuration',
          'tagQuery',
          ...autocompleteKeys,
        ];
      }

      return keys
        .filter(
          (key) =>
            !criteria.find((criterion) => criterion.key === key) ||
            criterion.key === key,
        )
        .filter((key) => key.includes(keyText));
    }

    let ss: string[];
    switch (keyText) {
      case 'serviceName':
        ss = serviceNames;
        break;
      case 'spanName':
        ss = spanNames;
        break;
      case 'remoteServiceName':
        ss = remoteServiceNames;
        break;
      default:
        if (autocompleteKeys.includes(keyText)) {
          ss = autocompleteValues;
          break;
        }
        return undefined;
    }
    return ss.filter((s) => s.includes(valueText));
  }, [
    autocompleteKeys,
    autocompleteValues,
    serviceNames,
    spanNames,
    remoteServiceNames,
    isEnteringKey,
    keyText,
    valueText,
    criteria,
    criterion,
  ]);

  useEffect(() => {
    if (autocompleteKeys.includes(keyText)) {
      loadAutocompleteValues(keyText);
    }
  }, [keyText, autocompleteKeys, loadAutocompleteValues]);

  const [suggestionIndex, setSuggestionIndex] = useState(-1);

  const handleChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setText(event.target.value);
      setFixedText(event.target.value);
      setSuggestionIndex(-1);
    },
    [],
  );

  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLInputElement>) => {
      switch (event.key) {
        case 'Enter':
          event.preventDefault();
          if (isEnteringKey) {
            if (!text) {
              onDecide(criterionIndex);
              return;
            }
            const newText = `${text}=`;
            setText(newText);
            setFixedText(newText);
            setSuggestionIndex(-1);
          } else {
            setFixedText(text);
            setSuggestionIndex(-1);
            onDecide(criterionIndex);
          }
          break;
        case 'ArrowUp': {
          event.preventDefault();
          if (
            isLoadingSuggestions ||
            !suggestions ||
            suggestions.length === 0 ||
            !(suggestionIndex > 0)
          ) {
            break;
          }
          const nextSuggestionIndex = suggestionIndex - 1;
          setSuggestionIndex(nextSuggestionIndex);
          if (isEnteringKey) {
            setText(suggestions[nextSuggestionIndex]);
          } else {
            setText(`${keyText}=${suggestions[nextSuggestionIndex]}`);
          }
          break;
        }
        case 'ArrowDown':
        case 'Tab': {
          event.preventDefault();
          if (
            isLoadingSuggestions ||
            !suggestions ||
            suggestions.length === 0
          ) {
            break;
          }
          let nextSuggestionIndex: number;
          if (suggestionIndex === suggestions.length - 1) {
            nextSuggestionIndex = 0;
          } else {
            nextSuggestionIndex = suggestionIndex + 1;
          }
          setSuggestionIndex(nextSuggestionIndex);
          if (isEnteringKey) {
            setText(suggestions[nextSuggestionIndex]);
          } else {
            setText(`${keyText}=${suggestions[nextSuggestionIndex]}`);
          }
          break;
        }
        case 'Escape': {
          onDecide(criterionIndex);
          break;
        }
        default:
          break;
      }
    },
    [
      isEnteringKey,
      text,
      onDecide,
      criterionIndex,
      isLoadingSuggestions,
      suggestions,
      suggestionIndex,
      keyText,
    ],
  );

  const handleDeleteButtonClick = useCallback(
    (event: React.MouseEvent<HTMLButtonElement>) => {
      event.stopPropagation();
      onDelete(criterionIndex);
    },
    [onDelete, criterionIndex],
  );

  const handleSuggestionItemClick = (index: number) => () => {
    if (!suggestions) {
      return;
    }
    if (isEnteringKey) {
      const newText = `${suggestions[index]}=`;
      setText(newText);
      setFixedText(newText);
      setSuggestionIndex(-1);
      if (inputEl.current) {
        // When the suggestion is clicked, the focus is removed from input.
        // So need to refocus.
        inputEl.current.focus();
      }
    } else {
      const newText = `${keyText}=${suggestions[index]}`;
      setText(newText);
      setFixedText(newText);
      setSuggestionIndex(-1);
      onDecide(criterionIndex);
    }
  };

  const handleClick = useCallback(() => {
    onFocus(criterionIndex);
  }, [criterionIndex, onFocus]);

  if (!isFocused) {
    return (
      <Root onClick={handleClick}>
        <Box
          maxWidth={150}
          height="100%"
          bgcolor="primary.dark"
          p={1}
          overflow="hidden"
          whiteSpace="nowrap"
          textOverflow="ellipsis"
        >
          {criterion.key}
        </Box>
        {criterion.value && (
          <Box
            maxWidth={200}
            height="100%"
            bgcolor="primary.main"
            p={1}
            overflow="hidden"
            whiteSpace="nowrap"
            textOverflow="ellipsis"
          >
            {criterion.value}
          </Box>
        )}
        <DeleteButton type="button" onClick={handleDeleteButtonClick}>
          <FontAwesomeIcon icon={faTimes} size="lg" />
        </DeleteButton>
      </Root>
    );
  }

  return (
    <ClickAwayListener onClickAway={onBlur}>
      <FocusedRoot>
        <Input
          ref={inputEl}
          value={text}
          onKeyDown={handleKeyDown}
          onChange={handleChange}
        />
        {(isLoadingSuggestions ||
          (suggestions && suggestions.length !== 0)) && (
          <SuggestionList
            suggestions={suggestions || []}
            isLoadingSuggestions={isLoadingSuggestions}
            suggestionIndex={suggestionIndex}
            onItemClick={handleSuggestionItemClick}
          />
        )}
        {!isEnteringKey &&
          (keyText === 'minDuration' ||
            keyText === 'maxDuration' ||
            keyText === 'tagQuery') && <HowToUse target={keyText} />}
      </FocusedRoot>
    </ClickAwayListener>
  );
};

export default CriterionBox;
