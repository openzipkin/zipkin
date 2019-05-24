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
  conditionKey: PropTypes.string.isRequired,
  conditionKeyOptions: PropTypes.arrayOf(
    PropTypes.shape({
      conditionKey: PropTypes.string.isRequired,
      isDisabled: PropTypes.bool.isRequired,
    }),
  ).isRequired,
  isFocused: PropTypes.bool.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
};

class GlobalSearchConditionKey extends React.Component {
  conditionKeyOptions() {
    const { conditionKeyOptions } = this.props;
    return conditionKeyOptions.map(conditionKeyOption => ({
      value: conditionKeyOption.conditionKey,
      label: conditionKeyOption.conditionKey,
      isDisabled: conditionKeyOption.isDisabled,
    }));
  }

  selectStyle() {
    const { conditionKey, conditionKeyOptions, isFocused } = this.props;

    let maxLength = 0;
    conditionKeyOptions.forEach((conditionKeyOption) => {
      const { length } = conditionKeyOption.conditionKey;
      if (maxLength < length) {
        maxLength = length;
      }
    });
    return {
      control: provided => ({
        ...provided,
        width: isFocused
          ? `${8 * maxLength + 16}px`
          : `${(8 * conditionKey.length) + 16}px`,
      }),
      indicatorsContainer: () => ({
        display: 'none',
      }),
      menuPortal: base => ({
        ...base,
        zIndex: 9999,
        width: `${8 * maxLength + 16}px`,
      }),
    };
  }

  render() {
    const {
      conditionKey,
      onFocus,
      onBlur,
      onChange,
    } = this.props;

    return (
      <div className="global-search-condition-key">
        <ReactSelect
          autoFocus
          isSearchable={false}
          value={{ value: conditionKey, label: conditionKey }}
          options={this.conditionKeyOptions()}
          onFocus={onFocus}
          onBlur={onBlur}
          onChange={(selected) => { onChange(selected.value); }}
          styles={this.selectStyle()}
          menuPortalTarget={document.body}
          defaultMenuIsOpen
          backspaceRemovesValue={false}
        />
      </div>
    );
  }
}

GlobalSearchConditionKey.propTypes = propTypes;

export default GlobalSearchConditionKey;
