import PropTypes from 'prop-types';
import React from 'react';

import { getServiceNameColor } from '../../util/color';

const propTypes = {
  serviceName: PropTypes.string.isRequired,
  count: PropTypes.number,
  className: PropTypes.string,
};

const defaultProps = {
  count: null,
  className: '',
};

const ServiceNameBadge = ({
  serviceName,
  count,
  className,
}) => {
  const style = {
    backgroundColor: getServiceNameColor(serviceName),
  };
  const text = count ? `${serviceName}x${count}` : serviceName;
  return (
    <span
      style={style}
      className={`service-name-badge ${className}`}
    >
      {text}
    </span>
  );
};

ServiceNameBadge.propTypes = propTypes;
ServiceNameBadge.defaultProps = defaultProps;

export default ServiceNameBadge;
