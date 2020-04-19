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
import { Box, CircularProgress } from '@material-ui/core';
import { createStyles, makeStyles, Theme } from '@material-ui/core/styles';
import classNames from 'classnames';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    list: {
      listStyleType: 'none',
      margin: 0,
      padding: 0,
      fontSize: '1.1rem',
    },
    listItem: {
      paddingTop: 8,
      paddingBottom: 8,
      paddingRight: 12,
      paddingLeft: 12,
      borderLeft: '5px solid rgba(0,0,0,0)',
      cursor: 'pointer',
      '&:hover': {
        backgroundColor: theme.palette.grey[300],
      },
    },
    'listItem--focused': {
      borderLeft: `5px solid ${theme.palette.primary.main}`,
    },
  }),
);

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
  const classes = useStyles();

  const listEls = React.useRef<HTMLLIElement[]>([]);
  const setListEl = (index: number) => (el: HTMLLIElement) => {
    listEls.current[index] = el;
  };

  React.useEffect(() => {
    if (suggestionIndex === -1) {
      return;
    }
    listEls.current[suggestionIndex].scrollIntoView(false);
  }, [suggestionIndex]);

  if (isLoadingSuggestions) {
    return (
      <Box
        position="absolute"
        top={45}
        left={0}
        right={0}
        borderRadius={3}
        bgcolor="background.paper"
        boxShadow={3}
        display="flex"
        alignItems="center"
        justifyContent="center"
        p={1}
      >
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box
      position="absolute"
      top={45}
      left={0}
      right={0}
      borderRadius={1}
      bgcolor="background.paper"
      boxShadow={3}
      maxHeight={300}
      overflow="auto"
    >
      <ul className={classes.list}>
        {suggestions.map((suggestion, index) => (
          // eslint-disable-next-line jsx-a11y/click-events-have-key-events,jsx-a11y/no-noninteractive-element-interactions
          <li
            ref={setListEl(index)}
            className={classNames(classes.listItem, {
              [classes['listItem--focused']]: suggestionIndex === index,
            })}
            onClick={onItemClick(index)}
          >
            {suggestion}
          </li>
        ))}
      </ul>
    </Box>
  );
};

export default SuggestionList;
