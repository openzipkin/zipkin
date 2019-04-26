/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import $ from 'jquery';

const defaults = {
  environment: '',
  suggestLens: false,
  queryLimit: 10,
  defaultLookback: 60 * 60 * 1000, // 1 hour
  searchEnabled: true,
  dependency: {
    lowErrorRate: 0.5, // 50% of calls in error turns line yellow
    highErrorRate: 0.75 // 75% of calls in error turns line red
  }
};

export default function loadConfig() {
  return $.ajax('config.json', {
    type: 'GET',
    dataType: 'json'
  }).then(data => function config(key) {
    if (data[key] === false) {
      return false;
    }

    return data[key] || defaults[key];
  });
}
