/*
 * Copyright 2015-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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
