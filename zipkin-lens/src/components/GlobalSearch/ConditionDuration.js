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
  value: PropTypes.number.isRequired,
  onConditionChange: PropTypes.func.isRequired,
  setNextFocusRef: PropTypes.func.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
};

const maxCharacters = 7;

const unitOptions = [
  'μs', 'ms', 's',
];

class ConditionDuration extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      unit: 'μs',
      isValueFocused: false,
      isUnitFocused: false,
      isUnitMenuOpened: false,
    };

    this.inputRef = undefined;
    this.unitRef = undefined;

    this.handleValueFocus = this.handleValueFocus.bind(this);
    this.handleValueBlur = this.handleValueBlur.bind(this);
    this.handleValueKeyDown = this.handleValueKeyDown.bind(this);
    this.handleValueChange = this.handleValueChange.bind(this);
    this.handleUnitFocus = this.handleUnitFocus.bind(this);
    this.handleUnitBlur = this.handleUnitBlur.bind(this);
    this.handleUnitChange = this.handleUnitChange.bind(this);
  }

  // The "value" of props is always passed in μs.
  // However, it is necessary to calculate by referring to the unit of
  // "state" for the displayed value.
  calculateDisplayedValue() {
    const { value } = this.props;
    const { unit } = this.state;
    switch (unit) {
      case 'μs':
        return value;
      case 'ms':
        return value / 1000;
      case 's':
        return value / (1000 * 1000);
      default:
        // Do nothing
        return null;
    }
  }

  handleValueFocus() {
    const { onFocus } = this.props;
    this.setState({ isValueFocused: true });
    onFocus();
  }

  handleValueBlur() {
    const { onBlur } = this.props;
    const { isUnitFocused } = this.state;
    this.setState({ isValueFocused: false });
    if (!isUnitFocused) {
      onBlur();
    }
  }

  handleUnitFocus() {
    const { onFocus } = this.props;
    this.setState({ isUnitFocused: true });
    onFocus();
    setTimeout(
      () => { this.setState({ isUnitMenuOpened: true }); }, 100,
    );
  }

  handleUnitBlur() {
    const { onBlur } = this.props;
    const { isValueFocused } = this.state;
    this.setState({ isUnitFocused: false });
    if (!isValueFocused) {
      onBlur();
    }
    this.setState({ isUnitMenuOpened: false });
  }

  handleValueKeyDown(e) {
    if (e.keyCode === 13) { // Enter key
      this.inputRef.blur();
      this.unitRef.focus();
    }
  }

  handleValueChange(event) {
    let value = parseInt(event.target.value, 10);
    if (Number.isNaN(value)) {
      value = 0;
    }
    const { onConditionChange } = this.props;
    const { unit } = this.state;

    switch (unit) {
      case 'μs':
        onConditionChange(String(value));
        break;
      case 'ms':
        onConditionChange(String(value * 1000));
        break;
      case 's':
        onConditionChange(String(value * 1000 * 1000));
        break;
      default:
        break;
    }
  }

  handleUnitChange(selected) {
    const { unit: prevUnit } = this.state;
    const { value, onConditionChange } = this.props;
    const unit = selected.value;

    this.setState({ unit });

    switch (prevUnit) {
      case 'μs':
        switch (unit) {
          case 'ms': onConditionChange(String(value * 1000)); break;
          case 's': onConditionChange(String(value * 1000 * 1000)); break;
          default: break; // Do nothing
        }
        break;
      case 'ms':
        switch (unit) {
          case 'μs': onConditionChange(String(value / 1000)); break;
          case 's': onConditionChange(String(value * 1000)); break;
          default: break; // Do nothing
        }
        break;
      case 's':
        switch (unit) {
          case 'μs': onConditionChange(String(value / (1000 * 1000))); break;
          case 'ms': onConditionChange(String(value / 1000)); break;
          default: break; // Do nothing
        }
        break;
      default: break; // Do nothing
    }
  }

  render() {
    const { setNextFocusRef, isFocused } = this.props;
    const { unit, isUnitMenuOpened } = this.state;
    const displayedValue = this.calculateDisplayedValue();

    return (
      <div className="condition-duration">
        <input
          ref={(ref) => {
            setNextFocusRef(ref);
            this.inputRef = ref;
          }}
          type="number"
          value={displayedValue}
          onChange={this.handleValueChange}
          className="condition-duration__value-input"
          style={{
            width: isFocused
              ? `${8 * maxCharacters + 20}px`
              : `${(8 * displayedValue.length + 20)}px`,
          }}
          onFocus={this.handleValueFocus}
          onBlur={this.handleValueBlur}
          onKeyDown={this.handleValueKeyDown}
          min={0}
        />
        <ReactSelect
          ref={(ref) => { this.unitRef = ref; }}
          isSearchable={false}
          value={{ value: unit, label: unit }}
          options={unitOptions.map(option => ({ value: option, label: option }))}
          styles={{
            control: provided => ({
              ...provided,
              width: '38px',
            }),
            // Disable because the dropdown indicator makes UX lower.
            indicatorsContainer: () => ({
              display: 'none',
            }),
            menuPortal: base => ({
              ...base,
              zIndex: 9999,
              width: '38px',
            }),
          }}
          // If we don't use portal, menu is hidden by the parent element.
          menuPortalTarget={document.body}
          onChange={this.handleUnitChange}
          classNamePrefix="condition-duration-unit-select"
          onFocus={this.handleUnitFocus}
          onBlur={this.handleUnitBlur}
          blurInputOnSelect
          menuIsOpen={isUnitMenuOpened}
        />
      </div>
    );
  }
}

ConditionDuration.propTypes = propTypes;

export default ConditionDuration;
