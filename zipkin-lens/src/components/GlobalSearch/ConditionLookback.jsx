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
import moment from 'moment';

import DatePicker from '../Common/DatePicker';

const lookbackOptions = [
  { value: '1h', label: '1 Hour' },
  { value: '2h', label: '2 Hours' },
  { value: '6h', label: '6 Hours' },
  { value: '12h', label: '12 Hours' },
  { value: '1d', label: '1 Day' },
  { value: '2d', label: '2 Days' },
  { value: '7d', label: '7 Days' },
  { value: 'custom', label: 'Custom' },
];

const propTypes = {
  lookback: PropTypes.shape({
    value: PropTypes.string.isRequired,
    endTs: PropTypes.number.isRequired,
    // "startTs" is needed only when "custom" is selected.
    startTs: PropTypes.number,
  }).isRequired,
  onLookbackChange: PropTypes.func.isRequired,
};

class ConditionLookback extends React.Component {
  constructor(props) {
    super(props);
    this.handleLookbackChange = this.handleLookbackChange.bind(this);
    this.handleEndTsChange = this.handleEndTsChange.bind(this);
    this.handleStartTsChange = this.handleStartTsChange.bind(this);
  }

  handleLookbackChange(selected) {
    const { lookback, onLookbackChange } = this.props;

    switch (selected.value) {
      case 'custom':
        onLookbackChange({
          value: 'custom',
          endTs: lookback.endTs,
          startTs: lookback.endTs,
        });
        break;
      default: // '1h', '2h', '7d', ...
        onLookbackChange({
          value: selected.value,
          endTs: lookback.endTs,
        });
        break;
    }
  }

  handleEndTsChange(endTime) {
    const { lookback, onLookbackChange } = this.props;

    onLookbackChange({
      value: 'custom',
      endTs: endTime.valueOf(),
      startTs: lookback.startTs,
    });
  }

  handleStartTsChange(startTime) {
    const { lookback, onLookbackChange } = this.props;

    onLookbackChange({
      value: 'custom',
      endTs: lookback.endTs,
      startTs: startTime.valueOf(),
    });
  }

  render() {
    const { lookback } = this.props;
    const selected = lookbackOptions.find(option => option.value === lookback.value);
    return (
      <div className="condition-lookback">
        <ReactSelect
          value={selected}
          options={lookbackOptions}
          styles={{
            control: provided => ({
              ...provided,
              width: '80px',
            }),
            indicatorsContainer: () => ({
              display: 'none',
            }),
            menuPortal: base => ({
              ...base,
              zIndex: 9999,
              width: '80px',
            }),
          }}
          // If we don't use portal, menu is hidden by the parent element.
          menuPortalTarget={document.body}
          classNamePrefix="condition-lookback-select"
          onChange={this.handleLookbackChange}
        />
        {
          lookback.value === 'custom'
            ? (
              <div className="condition-lookback__custom">
                <div>
                  <div className="condition-lookback__custom-date-picker">
                    <DatePicker
                      selected={moment(lookback.startTs)}
                      onChange={this.handleStartTsChange}
                    />
                  </div>
                  <div className="condition-lookback__custom-date-picker">
                    <div className="condition-lookback__custom-tilda-indicator">
                      -
                    </div>
                    <DatePicker
                      selected={moment(lookback.endTs)}
                      onChange={this.handleEndTsChange}
                    />
                  </div>
                </div>
              </div>
            )
            : null
        }
      </div>
    );
  }
}

ConditionLookback.propTypes = propTypes;

export default ConditionLookback;
