import PropTypes from 'prop-types';
import React from 'react';
import ReactSelect from 'react-select';
import moment from 'moment';

import Condition from './Condition';
import DatePicker from '../../../Common/DatePicker';

const propTypes = {
  onEndTsChange: PropTypes.func.isRequired,
  onLookbackChange: PropTypes.func.isRequired,
};

const options = [
  { value: '1h', label: '1 Hour' },
  { value: '2h', label: '2 Hours' },
  { value: '6h', label: '6 Hours' },
  { value: '12h', label: '12 Hours' },
  { value: '1d', label: '1 Day' },
  { value: '2d', label: '2 Days' },
  { value: '7d', label: '1 Week' },
  { value: 'custom', label: 'Custom' },
];

const convertValueToLabel = (value) => {
  const elem = options.find(
    e => e.value === value,
  );
  if (!elem) {
    return '';
  }
  return elem.label;
};

class Lookback extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      value: '1h',
      fromDate: moment(),
      toDate: moment(),
    };
    props.onEndTsChange(moment().valueOf());
    props.onLookbackChange(0);

    this.handleChange = this.handleChange.bind(this);
    this.handleFromDateChange = this.handleFromDateChange.bind(this);
    this.handleToDateChange = this.handleToDateChange.bind(this);
  }

  handleChange(selected) {
    const { onEndTsChange, onLookbackChange } = this.props;
    const { toDate, fromDate } = this.state;

    this.setState({ value: selected.value });

    switch (selected.value) {
      case '1h':
        onEndTsChange(moment().valueOf());
        onLookbackChange(3600000);
        break;
      case '3h':
        onEndTsChange(moment().valueOf());
        onLookbackChange(10800000);
        break;
      case '6h':
        onEndTsChange(moment().valueOf());
        onLookbackChange(21600000);
        break;
      case '12h':
        onEndTsChange(moment().valueOf());
        onLookbackChange(43200000);
        break;
      case '1d':
        onEndTsChange(moment().valueOf());
        onLookbackChange(86400000);
        break;
      case '2d':
        onEndTsChange(moment().valueOf());
        onLookbackChange(172800000);
        break;
      case '7d':
        onEndTsChange(moment().valueOf());
        onLookbackChange(604800000);
        break;
      case 'custom':
        if (toDate) {
          onEndTsChange(toDate.valueOf());
          if (fromDate) {
            onLookbackChange(toDate.diff(fromDate));
          }
        } else {
          onEndTsChange(moment());
          onLookbackChange(0);
        }
        break;
      default:
    }
  }

  handleFromDateChange(fromDate) {
    const { onLookbackChange } = this.props;
    const { toDate } = this.state;

    this.setState({ fromDate });
    if (toDate) {
      onLookbackChange(toDate.diff(fromDate));
    }
  }

  handleToDateChange(toDate) {
    const { onEndTsChange, onLookbackChange } = this.props;
    const { fromDate } = this.state;

    this.setState({ toDate });
    onEndTsChange(toDate.valueOf());
    if (fromDate) {
      onLookbackChange(toDate.diff(fromDate));
    }
  }

  render() {
    const { value, fromDate, toDate } = this.state;

    return (
      <div>
        <ReactSelect
          onChange={this.handleChange}
          className="react-select-container"
          classNamePrefix="react-select"
          options={options}
          value={{ value, label: convertValueToLabel(value) }}
        />
        {
          value === 'custom'
            ? (
              <div className="search__custom-lookback">
                <Condition label="From">
                  <DatePicker
                    onChange={this.handleFromDateChange}
                    selected={fromDate}
                    placeholder="From ..."
                  />
                </Condition>
                <Condition label="To">
                  <DatePicker
                    onChange={this.handleToDateChange}
                    selected={toDate}
                    placeholder="To ..."
                  />
                </Condition>
              </div>
            )
            : null
        }
      </div>
    );
  }
}

Lookback.propTypes = propTypes;

export default Lookback;
