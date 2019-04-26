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

const maxCharacters = 20;

const propTypes = {
  value: PropTypes.string.isRequired,
  onConditionChange: PropTypes.func.isRequired,
  setNextFocusRef: PropTypes.func.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
};

class ConditionTags extends React.Component {
  constructor(props) {
    super(props);
    this.inputRef = undefined;
    this.handleKeyDown = this.handleKeyDown.bind(this);
  }

  handleKeyDown(event) {
    if (event.key === 'Enter') {
      this.inputRef.blur();
    }
  }

  render() {
    const {
      value,
      onConditionChange,
      setNextFocusRef,
      onFocus,
      onBlur,
      isFocused,
    } = this.props;

    return (
      <div className="condition-tags">
        <input
          ref={(ref) => {
            setNextFocusRef(ref);
            this.inputRef = ref;
          }}
          type="text"
          value={value}
          onChange={(event) => { onConditionChange(event.target.value); }}
          className="condition-tags__input"
          style={{
            width: isFocused
              ? `${8 * maxCharacters + 16}px`
              : `${(8 * value.length) + 16}px`,
          }}
          onFocus={onFocus}
          onBlur={onBlur}
          onKeyDown={this.handleKeyDown}
        />
      </div>
    );
  }
}

ConditionTags.propTypes = propTypes;

export default ConditionTags;
