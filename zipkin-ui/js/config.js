const defaults = {
  queryLimit: 15
};

export default function(key) {
  if (!window.config) {
    return defaults[key];
  } else {
    return window.config[key] || defaults[key];
  }
}
