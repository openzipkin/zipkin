import $ from 'jquery';

const defaults = {
  environment: '',
  queryLimit: 10,
  defaultLookback: 60 * 60 * 1000 // 1 hour
};

export default function loadConfig() {
  return $.ajax('/config.json', {
    type: 'GET',
    dataType: 'json'
  }).then(data => function config(key) {
    return data[key] || defaults[key];
  });
}
