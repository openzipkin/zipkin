export const getErrorTypeColor = (errorType) => {
  switch (errorType) {
    case 'transient':
      return '#da8b8b';
    case 'critical':
      return '#da8b8b';
    default:
      return '#9bbdda';
  }
};

export const getInfoClassColor = (infoClass) => {
  switch (infoClass) {
    case 'trace-error-transient':
      return getErrorTypeColor('transient');
    case 'trace-error-critical':
      return getErrorTypeColor('critical');
    default:
      return getErrorTypeColor('none');
  }
};

export const getServiceNameColor = (serviceName) => {
  switch (serviceName.length % 10) {
    case 0:
      return '#00ACED';
    case 1:
      return '#5AE628';
    case 2:
      return '#CD201F';
    case 3:
      return '#D1AD59';
    case 4:
      return '#FF5A60';
    case 5:
      return '#563D7C';
    case 6:
      return '#00B489';
    case 7:
      return '#F8630E';
    case 8:
      return '#FF5700';
    default:
      return '#111111';
  }
};
