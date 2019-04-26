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
import React from 'react';
import ReactSelect from 'react-select';

const propTypes = {
  keyString: PropTypes.string.isRequired, // "key" is used by React, so use "keyString".
  keyOptions: PropTypes.arrayOf(
    PropTypes.shape({
      conditionKey: PropTypes.string,
      isAvailable: PropTypes.bool,
    }),
  ).isRequired,
  children: PropTypes.func.isRequired,
  onConditionKeyChange: PropTypes.func.isRequired,
  onDeleteButtonClick: PropTypes.func.isRequired,
  onKeyFocus: PropTypes.func,
  onKeyBlur: PropTypes.func,
  onValueFocus: PropTypes.func,
  onValueBlur: PropTypes.func,
};

const defaultProps = {
  onKeyFocus: undefined,
  onKeyBlur: undefined,
  onValueFocus: undefined,
  onValueBlur: undefined,
};

class SearchCondition extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      isKeyFocused: false,
      isKeyMenuOpened: false,
      isValueFocused: false,
    };
    // ref to the element to be focused after key is selected.
    // this ref is set by children.
    this.nextFocusRef = undefined;
    this.setNextFocusRef = this.setNextFocusRef.bind(this);

    this.handleKeyFocus = this.handleKeyFocus.bind(this);
    this.handleKeyBlur = this.handleKeyBlur.bind(this);
    this.handleValueFocus = this.handleValueFocus.bind(this);
    this.handleValueBlur = this.handleValueBlur.bind(this);
    this.handleKeyChange = this.handleKeyChange.bind(this);
  }

  getOptions() {
    const { keyOptions } = this.props;
    return keyOptions.map(keyOption => ({
      value: keyOption.conditionKey,
      label: keyOption.conditionKey,
      // Disable the already set condition
      isDisabled: !keyOption.isAvailable,
    }));
  }

  getMaxLengthOfKeyOptions() {
    const { keyOptions } = this.props;
    let max = 0;
    keyOptions.forEach((keyOption) => {
      const { length } = keyOption.conditionKey;
      if (max < length) {
        max = length;
      }
    });
    return max;
  }

  setNextFocusRef(ref) {
    this.nextFocusRef = ref;
  }

  handleKeyFocus() {
    const { onKeyFocus } = this.props;
    if (onKeyFocus) {
      onKeyFocus();
    }
    this.setState({ isKeyFocused: true });
    setTimeout(() => { this.setState({ isKeyMenuOpened: true }); }, 30);
  }

  handleKeyBlur() {
    const { onKeyBlur } = this.props;
    if (onKeyBlur) {
      onKeyBlur();
    }
    this.setState({ isKeyFocused: false });
    setTimeout(() => { this.setState({ isKeyMenuOpened: false }); }, 30);
  }

  handleValueFocus() {
    const { onValueFocus } = this.props;
    if (onValueFocus) {
      onValueFocus();
    }
    this.setState({ isValueFocused: true });
  }

  handleValueBlur() {
    const { onValueBlur } = this.props;
    if (onValueBlur) {
      onValueBlur();
    }
    this.setState({ isValueFocused: false });
  }

  handleKeyChange(selected) {
    const { onConditionKeyChange } = this.props;
    onConditionKeyChange(selected.value);
    if (this.nextFocusRef) {
      // At this timing, it is possible that the child component
      // is not yet mounted, so use setTimeout to delay.
      setTimeout(() => { this.nextFocusRef.focus(); }, 0);
    }
  }

  render() {
    const {
      keyString,
      children,
      onDeleteButtonClick,
    } = this.props;
    const { isKeyFocused, isValueFocused, isKeyMenuOpened } = this.state;

    const maxLengthOfKeyOptions = this.getMaxLengthOfKeyOptions();

    return (
      <div className="search-condition">
        <div className="search-condition__key">
          <ReactSelect
            autoFocus
            isSearchable={false}
            value={{ value: keyString, label: keyString }}
            options={this.getOptions()}
            onFocus={this.handleKeyFocus}
            onBlur={this.handleKeyBlur}
            onChange={this.handleKeyChange}
            styles={{
              control: provided => ({
                ...provided,
                width: isKeyFocused || isValueFocused
                  // When this component is focused, change the width of the
                  // entier component according to the length of the max length
                  // of the option keys.
                  ? `${8 * maxLengthOfKeyOptions + 16}px`
                  // When this component is not focused, change the width of
                  // the entire component according to the length of the name
                  // of the key.
                  : `${(8 * keyString.length) + 16}px`,
              }),
              // Disable because the dropdown indicator makes UX lower.
              indicatorsContainer: () => ({
                display: 'none',
              }),
              menuPortal: base => ({
                ...base,
                zIndex: 9999,
                width: `${8 * maxLengthOfKeyOptions + 16}px`,
              }),
            }}
            // If we don't use portal, menu is hidden by the parent element.
            menuPortalTarget={document.body}
            classNamePrefix="search-condition-key-select"
            defaultMenuIsOpen
            backspaceRemovesValue={false}
            menuIsOpen={isKeyMenuOpened}
          />
        </div>
        <div className="search-condition__value">
          {
            children({
              onFocus: this.handleValueFocus,
              onBlur: this.handleValueBlur,
              setNextFocusRef: this.setNextFocusRef,
              isFocused: isKeyFocused || isValueFocused,
            })
          }
        </div>
        <div
          className="search-condition__delete-button"
          role="presentation"
          onClick={onDeleteButtonClick}
        >
          <span className="fas fa-times search-condition__delete-button-icon" />
        </div>
      </div>
    );
  }
}

SearchCondition.propTypes = propTypes;
SearchCondition.defaultProps = defaultProps;

export default SearchCondition;
