/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { CircularProgress } from '@material-ui/core';
import React, { useEffect, useRef } from 'react';
import styled from 'styled-components';
import { getTheme } from '../../../util/theme';

const Root = styled.div<{ isLoading: boolean }>`
  position: absolute;
  top: 45px;
  left: 0px;
  right: 0px;
  border-radius: 1px;
  background-color: ${({ theme }) => theme.palette.background.paper};
  box-shadow: ${({ theme }) => theme.shadows[3]};
  max-height: 300px;
  overflow: auto;
  z-index: ${({ theme }) => theme.zIndex.modal};

  ${({ isLoading, theme }) =>
    !isLoading
      ? ''
      : `
        display: flex;
        align-items: center;
        justify-content: center;
        padding: ${theme.spacing(1)}px;
      `}
`;

const List = styled.ul`
  list-style-type: none;
  margin: 0px;
  padding: 0px;
  font-size: 1rem;
`;

const ListItem = styled.li<{ isFocused: boolean }>`
  padding-top: 8px;
  padding-bottom: 8px;
  padding-right: 12px;
  padding-left: 12px;
  border-left: ${({ isFocused, theme }) =>
    `5px solid ${isFocused ? theme.palette.primary.main : 'rgba(0, 0, 0, 0)'}`};
  cursor: pointer;
  &:hover {
    background-color: ${({ theme }) =>
      getTheme() === 'dark'
        ? theme.palette.grey[600]
        : theme.palette.grey[300]};
  }
`;

interface SuggestionListProps {
  suggestions: string[];
  isLoadingSuggestions: boolean;
  suggestionIndex: number;
  onItemClick: (index: number) => () => void;
}

const SuggestionList: React.FC<SuggestionListProps> = ({
  suggestions,
  isLoadingSuggestions,
  suggestionIndex,
  onItemClick,
}) => {
  const listEls = useRef<HTMLLIElement[]>([]);
  const setListEl = (index: number) => (el: HTMLLIElement) => {
    listEls.current[index] = el;
  };

  useEffect(() => {
    if (suggestionIndex === -1) {
      return;
    }
    listEls.current[suggestionIndex].scrollIntoView(false);
  }, [suggestionIndex]);

  if (isLoadingSuggestions) {
    return (
      <Root isLoading>
        <CircularProgress size={20} />
      </Root>
    );
  }

  return (
    <Root isLoading={false}>
      <List>
        {suggestions.map((suggestion, index) => (
          <ListItem
            key={suggestion}
            ref={setListEl(index)}
            isFocused={suggestionIndex === index}
            onClick={onItemClick(index)}
          >
            {suggestion}
          </ListItem>
        ))}
      </List>
    </Root>
  );
};

export default SuggestionList;
