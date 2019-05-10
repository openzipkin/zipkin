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
import RCCalendar from 'rc-calendar';
import RCDatePicker from 'rc-calendar/lib/Picker';
import RCTimePicker from 'rc-time-picker';
import moment from 'moment';

const propTypes = {
  selected: PropTypes.shape({}).isRequired,
  onChange: PropTypes.func.isRequired,
};

class DatePicker extends React.Component {
  constructor(props) {
    super(props);
    this.handleDateChange = this.handleDateChange.bind(this);
    this.handleTimeChange = this.handleTimeChange.bind(this);
  }

  handleDateChange(date) {
    const { selected, onChange } = this.props;
    onChange(
      moment({
        year: date.year(),
        month: date.month(),
        day: date.date(),
        hour: selected.hour(),
        minute: selected.minute(),
        second: selected.second(),
        millisecond: selected.millisecond(),
      }),
    );
  }

  handleTimeChange(time) {
    const { selected, onChange } = this.props;
    onChange(
      moment({
        year: selected.year(),
        month: selected.month(),
        day: selected.date(),
        hour: time.hour(),
        minute: time.minute(),
        second: time.second(),
        millisecond: time.millisecond(),
      }),
    );
  }

  render() {
    const { selected } = this.props;
    return (
      <div className="date-picker">
        <RCDatePicker
          className="date-picker__calendar"
          calendar={(<RCCalendar />)}
          onChange={this.handleDateChange}
        >
          {
            () => (
              <span>
                <input
                  className="date-picker__calendar-input"
                  value={selected ? selected.format('MMM Do YY') : ''}
                  type="text"
                  readOnly
                />
              </span>
            )
          }
        </RCDatePicker>
        <RCTimePicker
          className="date-picker__timepicker"
          onChange={this.handleTimeChange}
          value={selected}
        />
      </div>
    );
  }
}

DatePicker.propTypes = propTypes;

export default DatePicker;
