import $ from 'jquery';

const defaults = {
  environment: '',
  queryLimit: 15,
  defaultLookback: 24 * 60 * 60 * 1000 // 24 hours
};

export default function loadConfig() {
  return $.ajax('/config.json', {
    type: 'GET',
    dataType: 'json'
  }).then(data => function config(key) {
    return data[key] || defaults[key];
  });
}
