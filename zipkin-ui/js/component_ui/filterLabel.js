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

export default component(function filterLabel() {
  this.serviceName = '';

  this.toggleFilter = function() {
    const evt = this.$node.is('.service-tag-filtered') ?
      'uiRemoveServiceNameFilter' :
      'uiAddServiceNameFilter';
    this.trigger(evt, {value: this.serviceName});
  };

  this.filterAdded = function(e, data) {
    if (data.value === this.serviceName) {
      this.$node.addClass('service-tag-filtered');
    }
  };

  this.filterRemoved = function(e, data) {
    if (data.value === this.serviceName) {
      this.$node.removeClass('service-tag-filtered');
    }
  };

  this.after('initialize', function() {
    this.serviceName = this.$node.data('serviceName');
    this.on('click', this.toggleFilter);
    this.on(document, 'uiAddServiceNameFilter', this.filterAdded);
    this.on(document, 'uiRemoveServiceNameFilter', this.filterRemoved);
  });
});
