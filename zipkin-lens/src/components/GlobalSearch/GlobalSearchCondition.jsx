/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import PropTypes from 'prop-types';
import React, { useState } from 'react';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import Paper from '@material-ui/core/Paper';

import GlobalSearchConditionKey from './GlobalSearchConditionKey';
import GlobalSearchConditionValue from './GlobalSearchConditionValue';

const useStyles = makeStyles(theme => ({
  root: {
    display: 'flex',
    alignItems: 'center',
    overflow: 'hidden',
    height: '2.4rem',
  },
  deleteButton: {
    minWidth: '2.4rem',
    width: '2.4rem',
    height: '100%',
    fontSize: '1.2rem',
    boxShadow: 'none',
    borderRadius: 0,
    color: theme.palette.primary.contrastText,
    backgroundColor: theme.palette.primary.light,
    '&:hover': {
      backgroundColor: theme.palette.primary.main,
    },
  },
}));

const propTypes = {
  conditionIndex: PropTypes.number.isRequired,
  deleteCondition: PropTypes.func.isRequired,
};

const GlobalSearchCondition = ({
  conditionIndex,
  deleteCondition,
}) => {
  const classes = useStyles();

  const [isKeyFocused, setIsKeyFocused] = useState(false);
  const [isValueFocused, setIsValueFocused] = useState(false);

  const handleKeyFocus = () => setIsKeyFocused(true);
  const handleKeyBlur = () => setIsKeyFocused(false);
  const handleValueFocus = () => setIsValueFocused(true);
  const handleValueBlur = () => setIsValueFocused(false);

  const handleDeleteButtonClick = () => {
    deleteCondition(conditionIndex);
  };

  return (
    <Paper
      className={classes.root}
    >
      <GlobalSearchConditionKey
        conditionIndex={conditionIndex}
        isFocused={isKeyFocused}
        onFocus={handleKeyFocus}
        onBlur={handleKeyBlur}
      />
      <GlobalSearchConditionValue
        conditionIndex={conditionIndex}
        isFocused={isValueFocused}
        onFocus={handleValueFocus}
        onBlur={handleValueBlur}
      />
      <Button
        variant="contained"
        onClick={handleDeleteButtonClick}
        className={classes.deleteButton}
      >
        <Box component="span" className="fas fa-times" />
      </Button>
    </Paper>
  );
};

/*
class GlobalSearchCondition extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      isKeyFocused: false,
      isValueFocused: false,
    };

    this.handleKeyFocus = this.handleKeyFocus.bind(this);
    this.handleKeyBlur = this.handleKeyBlur.bind(this);
    this.handleKeyChange = this.handleKeyChange.bind(this);
    this.handleValueFocus = this.handleValueFocus.bind(this);
    this.handleValueBlur = this.handleValueBlur.bind(this);
    this.handleValueChange = this.handleValueChange.bind(this);
    this.handleDeleteButtonClick = this.handleDeleteButtonClick.bind(this);
  }

  getConditionKey() {
    const { conditions, conditionIndex } = this.props;
    return conditions[conditionIndex].key;
  }

  getConditionValue() {
    const { conditions, conditionIndex } = this.props;
    return conditions[conditionIndex].value;
  }

  handleKeyFocus() {
    this.setState({ isKeyFocused: true });
  }

  handleKeyBlur() {
    this.setState({ isKeyFocused: false });
  }

  handleKeyChange(value) {
    const {
      conditionIndex,
      autocompleteKeys,
      changeConditionKey,
      fetchAutocompleteValues,
    } = this.props;

    changeConditionKey(conditionIndex, value);

    if (autocompleteKeys.includes(value)) {
      fetchAutocompleteValues(value);
    }
  }

  handleValueFocus() {
    this.setState({ isValueFocused: true });
  }

  handleValueBlur() {
    this.setState({ isValueFocused: false });
  }

  handleValueChange(value) {
    const {
      conditionIndex,
      fetchRemoteServices,
      fetchSpans,
      changeConditionValue,
    } = this.props;

    changeConditionValue(conditionIndex, value);

    if (this.getConditionKey() === 'serviceName') {
      fetchRemoteServices(value);
      fetchSpans(value);
    }
  }

  handleDeleteButtonClick() {
    const { deleteCondition, conditionIndex } = this.props;
    deleteCondition(conditionIndex);
  }

  isFocused() {
    const { isKeyFocused, isValueFocused } = this.state;
    return isKeyFocused || isValueFocused;
  }

  renderConditionValue() {
    const {
      services,
      remoteServices,
      spans,
      autocompleteValues,
    } = this.props;

    const conditionKey = this.getConditionKey();
    const conditionValue = this.getConditionValue();

    const commonProps = {
      value: conditionValue,
      onChange: this.handleValueChange,
    };

    switch (conditionKey) {
      case 'serviceName':
      case 'remoteServiceName':
      case 'spanName': {
        let options;
        if (conditionKey === 'serviceName') {
          options = services;
        } else if (conditionKey === 'remoteServiceName') {
          options = remoteServices;
        } else if (conditionKey === 'spanName') {
          options = spans;
        }
        return (
          <GlobalSearchNameCondition
            {...commonProps}
            options={options}
            onFocus={this.handleValueFocus}
            onBlur={this.handleValueBlur}
            setFocusableElement={this.setFocusableElement}
            isFocused={this.isFocused()}
          />
        );
      }
      default: // autocompleteTags
        return (
          <GlobalSearchNameCondition
            {...commonProps}
            options={autocompleteValues}
            onFocus={this.handleValueFocus}
            onBlur={this.handleValueBlur}
            setFocusableElement={this.setFocusableElement}
            isFocused={this.isFocused()}
          />
        );
    }
  }

  render() {
    const { conditions, autocompleteKeys } = this.props;
    const { isKeyFocused } = this.state;

    const conditionKey = this.getConditionKey();

    return (
      <div className="global-search-condition">
        <div className="global-search-condition__key-wrapper">
          <GlobalSearchConditionKey
            conditionKey={conditionKey}
            conditionKeyOptions={
              buildConditionKeyOptions(conditionKey, conditions, autocompleteKeys)
            }
            isFocused={isKeyFocused}
            onFocus={this.handleKeyFocus}
            onBlur={this.handleKeyBlur}
            onChange={this.handleKeyChange}
          />
        </div>
        <div className="global-search-condition__value-wrapper">
          {this.renderConditionValue()}
        </div>
        <div className="global-search-condition__delete-button-wrapper">
          <button
            type="button"
            onClick={this.handleDeleteButtonClick}
            className="global-search-condition__delete-button"
          >
            <span className="fas fa-times global-search-condition__delete-button-icon" />
          </button>
        </div>
      </div>
    );
  }
}
*/
GlobalSearchCondition.propTypes = propTypes;

export default GlobalSearchCondition;
