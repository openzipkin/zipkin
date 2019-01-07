import PropTypes from 'prop-types';
import React from 'react';

const propTypes = {
  limit: PropTypes.number.isRequired,
  onLimitChange: PropTypes.func.isRequired,
};

class ConditionLimit extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      isFocused: false,
    };
    this.inputRef = undefined;
    this.handleKeyDown = this.handleKeyDown.bind(this);
    this.handleFocus = this.handleFocus.bind(this);
    this.handleBlur = this.handleBlur.bind(this);
  }

  handleKeyDown(e) {
    if (e.keyCode === 13) {
      this.inputRef.blur();
    }
  }

  handleFocus() {
    this.setState({ isFocused: true });
  }

  handleBlur() {
    this.setState({ isFocused: false });
  }

  render() {
    const { isFocused } = this.state;
    const { limit, onLimitChange } = this.props;

    return (
      <div className="condition-limit">
        {
          isFocused
            ? (
              <input
                ref={(ref) => { this.inputRef = ref; }}
                type="number"
                value={limit}
                onChange={(event) => {
                  let newLimit = parseInt(event.target.value, 10);
                  if (Number.isNaN(newLimit)) {
                    newLimit = 0;
                  }
                  onLimitChange(newLimit);
                }}
                className="condition-limit__input condition-limit__input--focused"
                onKeyDown={this.handleKeyDown}
                onFocus={this.handleFocus}
                onBlur={this.handleBlur}
                min={0}
              />
            )
            : (
              <div
                className="condition-limit__input"
                role="presentation"
                onClick={
                  () => {
                    this.handleFocus();
                    setTimeout(() => { this.inputRef.focus(); }, 0);
                  }
                }
              >
                {`Max ${limit}`}
              </div>
            )
        }
      </div>
    );
  }
}

ConditionLimit.propTypes = propTypes;

export default ConditionLimit;
