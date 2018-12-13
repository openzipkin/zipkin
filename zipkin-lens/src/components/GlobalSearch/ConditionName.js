import PropTypes from 'prop-types';
import React from 'react';
import ReactSelect from 'react-select';

const propTypes = {
  value: PropTypes.string.isRequired,
  options: PropTypes.arrayOf(PropTypes.string).isRequired,
  onConditionChange: PropTypes.func.isRequired,
  setNextFocusRef: PropTypes.func.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
};

class ConditionName extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      isMenuOpened: false,
    };
  }

  getMaxLengthOfOptions() {
    const { options } = this.props;
    let max = 0;
    options.forEach((option) => {
      const { length } = option;
      if (max < length) {
        max = length;
      }
    });
    return max;
  }

  render() {
    const {
      value,
      options,
      onConditionChange,
      setNextFocusRef,
      onFocus,
      onBlur,
      isFocused,
    } = this.props;

    const {
      isMenuOpened,
    } = this.state;

    const maxLengthOfOptions = this.getMaxLengthOfOptions();
    return (
      <div className="condition-name">
        <ReactSelect
          ref={(ref) => { setNextFocusRef(ref); }}
          value={{ value, label: value }}
          options={options.map(option => ({ value: option, label: option }))}
          onFocus={
            () => {
              onFocus();
              setTimeout(() => { this.setState({ isMenuOpened: true }); }, 100);
            }
          }
          onBlur={
            () => {
              onBlur();
              this.setState({ isMenuOpened: false });
            }
          }
          styles={{
            control: provided => ({
              ...provided,
              width: isFocused
                // When this component is focused, change the width of the
                // entier component according to the length of the max length
                // of the option strings.
                ? `${8 * maxLengthOfOptions + 16}px`
                // When this component is not focused, change the width of
                // the entire component according to the length of the value.
                : `${(8 * value.length) + 16}px`,
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
        />
      </div>
    );
  }
}

ConditionName.propTypes = propTypes;

export default ConditionName;
