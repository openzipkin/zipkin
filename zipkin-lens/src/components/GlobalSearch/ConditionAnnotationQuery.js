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

// TODO: After specific-tags feature is completed in server-side,
// I must reimplement this component!
class ConditionAnnotationQuery extends React.Component {
  constructor(props) {
    super(props);
    this.inputRef = undefined;
    this.handleKeyDown = this.handleKeyDown.bind(this);
  }

  handleKeyDown(e) {
    if (e.keyCode === 13) {
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
      <div className="condition-annotation-query">
        <input
          ref={(ref) => {
            setNextFocusRef(ref);
            this.inputRef = ref;
          }}
          type="text"
          value={value}
          onChange={(event) => { onConditionChange(event.target.value); }}
          className="condition-annotation-query__input"
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

ConditionAnnotationQuery.propTypes = propTypes;

export default ConditionAnnotationQuery;
