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

import { buildReactSelectStyle } from './util';

const propTypes = {
  value: PropTypes.string,
  options: PropTypes.arrayOf(PropTypes.string).isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
  setFocusableElement: PropTypes.func.isRequired,
};

const defaultProps = {
  value: undefined,
};

class GlobalSearchNameCondition extends React.Component {
  buildReactSelectStyle() {
    const { value, options, isFocused } = this.props;
    return buildReactSelectStyle(value, options, isFocused);
  }

  render() {
    const {
      value,
      options,
      onFocus,
      onBlur,
      onChange,
      setFocusableElement,
    } = this.props;

    return (
      <div className="global-search-name-condition">
        <ReactSelect
          ref={setFocusableElement}
          value={{ value, label: value }}
          options={options.map(opt => ({ value: opt, label: opt }))}
          styles={this.buildReactSelectStyle()}
          onFocus={onFocus}
          onBlur={onBlur}
          onChange={(selected) => { onChange(selected.value); }}
          menuPortalTarget={document.body}
          blurInputOnSelect
        />
      </div>
    );
  }
}

GlobalSearchNameCondition.propTypes = propTypes;
GlobalSearchNameCondition.defaultProps = defaultProps;

export default GlobalSearchNameCondition;
