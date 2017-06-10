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
import {component} from 'flightjs';

export default component(function goToDependency() {
  this.navigateToDependency = function(evt) {
    evt.preventDefault();
    const endTs = document.getElementById('endTs').value;
    const startTs = document.getElementById('startTs').value;
    window.location.href = `/dependency?endTs=${endTs}&startTs=${startTs}`;
  };

  this.after('initialize', function() {
    this.on('submit', this.navigateToDependency);
  });
});
