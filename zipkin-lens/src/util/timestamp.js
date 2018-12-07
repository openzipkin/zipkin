export const formatDuration = (duration) => {
  if (duration === 0 || typeof duration === 'undefined') {
    return '0ms';
  }
  if (duration < 1000) {
    return `${duration}μ`;
  }
  if (duration < 1000000) {
    return `${(duration / 1000).toFixed(3)}ms`;
  }
  return `${(duration / 1000000).toFixed(3)}s`;
};
