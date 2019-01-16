import PropTypes from 'prop-types';
import React from 'react';
import ReactSelect from 'react-select';

const propTypes = {
  keyString: PropTypes.string.isRequired, // "key" is used by React, so use "keyString".
  keyOptions: PropTypes.arrayOf(
    PropTypes.shape({
      conditionKey: PropTypes.string,
      isAvailable: PropTypes.bool,
    }),
  ).isRequired,
  children: PropTypes.func.isRequired,
  onConditionKeyChange: PropTypes.func.isRequired,
  onDeleteButtonClick: PropTypes.func.isRequired,
};

class SearchCondition extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      isKeyFocused: false,
      isKeyMenuOpened: false,
      isValueFocused: false,
    };
    // ref to the element to be focused after key is selected.
    // this ref is set by children.
    this.nextFocusRef = undefined;
    this.setNextFocusRef = this.setNextFocusRef.bind(this);

    this.handleKeyFocus = this.handleKeyFocus.bind(this);
    this.handleKeyBlur = this.handleKeyBlur.bind(this);
    this.handleValueFocus = this.handleValueFocus.bind(this);
    this.handleValueBlur = this.handleValueBlur.bind(this);
    this.handleKeyChange = this.handleKeyChange.bind(this);
  }

  getOptions() {
    const { keyOptions } = this.props;
    return keyOptions.map(keyOption => ({
      value: keyOption.conditionKey,
      label: keyOption.conditionKey,
      // Disable the already set condition
      isDisabled: !keyOption.isAvailable,
    }));
  }

  getMaxLengthOfKeyOptions() {
    const { keyOptions } = this.props;
    let max = 0;
    keyOptions.forEach((keyOption) => {
      const { length } = keyOption.conditionKey;
      if (max < length) {
        max = length;
      }
    });
    return max;
  }

  setNextFocusRef(ref) {
    this.nextFocusRef = ref;
  }

  handleKeyFocus() {
    this.setState({ isKeyFocused: true });
    setTimeout(() => { this.setState({ isKeyMenuOpened: true }); }, 10);
  }

  handleKeyBlur() {
    this.setState({ isKeyFocused: false });
    setTimeout(() => { this.setState({ isKeyMenuOpened: false }); }, 10);
  }

  handleValueFocus() {
    this.setState({ isValueFocused: true });
  }

  handleValueBlur() {
    this.setState({ isValueFocused: false });
  }

  handleKeyChange(selected) {
    const { onConditionKeyChange } = this.props;
    onConditionKeyChange(selected.value);
    if (this.nextFocusRef) {
      // At this timing, it is possible that the child component
      // is not yet mounted, so use setTimeout to delay.
      setTimeout(() => { this.nextFocusRef.focus(); }, 0);
    }
  }

  render() {
    const {
      keyString,
      children,
      onDeleteButtonClick,
    } = this.props;
    const { isKeyFocused, isValueFocused, isKeyMenuOpened } = this.state;

    const maxLengthOfKeyOptions = this.getMaxLengthOfKeyOptions();

    return (
      <div className="search-condition">
        <div className="search-condition__key">
          <ReactSelect
            autoFocus
            isSearchable={false}
            value={{ value: keyString, label: keyString }}
            options={this.getOptions()}
            onFocus={this.handleKeyFocus}
            onBlur={this.handleKeyBlur}
            onChange={this.handleKeyChange}
            styles={{
              control: provided => ({
                ...provided,
                width: isKeyFocused || isValueFocused
                  // When this component is focused, change the width of the
                  // entier component according to the length of the max length
                  // of the option keys.
                  ? `${8 * maxLengthOfKeyOptions + 16}px`
                  // When this component is not focused, change the width of
                  // the entire component according to the length of the name
                  // of the key.
                  : `${(8 * keyString.length) + 16}px`,
              }),
              // Disable because the dropdown indicator makes UX lower.
              indicatorsContainer: () => ({
                display: 'none',
              }),
              menuPortal: base => ({
                ...base,
                zIndex: 9999,
                width: `${8 * maxLengthOfKeyOptions + 16}px`,
              }),
            }}
            // If we don't use portal, menu is hidden by the parent element.
            menuPortalTarget={document.body}
            classNamePrefix="search-condition-key-select"
            defaultMenuIsOpen
            backspaceRemovesValue={false}
            menuIsOpen={isKeyMenuOpened}
          />
        </div>
        <div className="search-condition__value">
          {
            children({
              onFocus: this.handleValueFocus,
              onBlur: this.handleValueBlur,
              setNextFocusRef: this.setNextFocusRef,
              isFocused: isKeyFocused || isValueFocused,
            })
          }
        </div>
        <div
          className="search-condition__delete-button"
          role="presentation"
          onClick={onDeleteButtonClick}
        >
          <span className="fas fa-times search-condition__delete-button-icon" />
        </div>
      </div>
    );
  }
}

SearchCondition.propTypes = propTypes;

export default SearchCondition;
