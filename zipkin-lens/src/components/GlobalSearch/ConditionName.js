import PropTypes from 'prop-types';
import React from 'react';
import ReactSelect from 'react-select';

const propTypes = {
  value: PropTypes.string,
  options: PropTypes.arrayOf(PropTypes.string).isRequired,
  onConditionChange: PropTypes.func.isRequired,
  setNextFocusRef: PropTypes.func.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
  isLoadingOptions: PropTypes.bool.isRequired,
};

const defaultProps = {
  value: undefined,
};

class ConditionName extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      isMenuOpened: false,
    };
    this.handleFocus = this.handleFocus.bind(this);
    this.handleBlur = this.handleBlur.bind(this);
  }

  getMaxLengthOfOptions() {
    const { options } = this.props;
    let max = 4;
    options.forEach((option) => {
      const { length } = option;
      if (max < length) {
        max = length;
      }
    });
    return max;
  }

  getOptions() {
    const { options } = this.props;
    const result = [];
    options.forEach((option) => {
      result.push({
        value: option,
        label: option,
      });
    });
    return result;
  }

  handleFocus() {
    const { onFocus } = this.props;
    onFocus();
    setTimeout(() => { this.setState({ isMenuOpened: true }); }, 100);
  }

  handleBlur() {
    const { onBlur } = this.props;
    onBlur();
    this.setState({ isMenuOpened: false });
  }

  calculateOptimumWidth() {
    const { isFocused, value } = this.props;
    const maxLengthOfOptions = this.getMaxLengthOfOptions();
    if (isFocused) {
      return `${8 * maxLengthOfOptions + 16}px`;
    }
    if (!value) {
      return '36px';
    }
    return `${(8 * value.length) + 16}px`;
  }

  render() {
    const {
      value,
      onConditionChange,
      setNextFocusRef,
      isLoadingOptions,
    } = this.props;
    const { isMenuOpened } = this.state;

    const options = this.getOptions();
    const maxLengthOfOptions = this.getMaxLengthOfOptions();
    return (
      <div className="condition-name">
        <ReactSelect
          ref={(ref) => { setNextFocusRef(ref); }}
          value={{ value, label: value }}
          options={options}
          onFocus={this.handleFocus}
          onBlur={this.handleBlur}
          styles={{
            control: provided => ({
              ...provided,
              width: this.calculateOptimumWidth(),
            }),
            // Disable because the dropdown indicator makes UX lower.
            indicatorsContainer: () => ({
              display: 'none',
            }),
            menuPortal: base => ({
              ...base,
              zIndex: 9999,
              width: `${8 * maxLengthOfOptions + 16}px`,
            }),
          }}
          onChange={(selected) => {
            onConditionChange(selected.value);
          }}
          menuPortalTarget={
            /* If we don't use portal, menu is hidden by the parent element. */
            document.body
          }
          classNamePrefix="condition-name-select"
          blurInputOnSelect
          menuIsOpen={isMenuOpened}
          isLoading={isLoadingOptions}
        />
      </div>
    );
  }
}

ConditionName.propTypes = propTypes;
ConditionName.defaultProps = defaultProps;

export default ConditionName;
