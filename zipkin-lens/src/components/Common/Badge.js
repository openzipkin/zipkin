import PropTypes from 'prop-types';
import React from 'react';

import { getServiceNameColor } from '../../util/color';

const propTypes = {
  value: PropTypes.string.isRequired,
  text: PropTypes.string.isRequired,
  className: PropTypes.string,
  onClick: PropTypes.func,
};

const defaultProps = {
  className: '',
  onClick: null,
};

const Badge = ({
  value,
  text,
  onClick,
  className,
}) => {
  const color = getServiceNameColor(value);
  const style = { background: color };
  return (
    <span
      className={`badge ${className}`}
      onClick={
        (e) => {
          if (onClick) {
            e.stopPropagation();
            onClick(value);
          }
        }
      }
      role="presentation"
    >
      <span style={style} className="badge__color" />
      {text}
    </span>
  );
};

Badge.propTypes = propTypes;
Badge.defaultProps = defaultProps;

export default Badge;
