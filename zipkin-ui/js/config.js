import $ from 'jquery';

const defaults = {
  environment: '',
  queryLimit: 15
};

export default function loadConfig() {
  return $.ajax('/config.json', {
    type: 'GET',
    dataType: 'json'
  }).then(data => function config(key) {
    return data[key] || defaults[key];
  });
}
