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
import {component} from 'flightjs';
import $ from 'jquery';

export default component(function traces() {
  this.$traces = [];

  this.sortFunctions = {
    'service-percentage-desc': (a, b) => b.percentage - a.percentage,
    'service-percentage-asc': (a, b) => a.percentage - b.percentage,
    'duration-desc': (a, b) => b.duration - a.duration,
    'duration-asc': (a, b) => a.duration - b.duration,
    'timestamp-desc': (a, b) => b.timestamp - a.timestamp,
    'timestamp-asc': (a, b) => a.timestamp - b.timestamp
  };

  this.updateSortOrder = function(ev, data) {
    if (this.sortFunctions.hasOwnProperty(data.order)) {
      this.$traces.sort(this.sortFunctions[data.order]);
      this.$node.html(this.$traces);
    }
  };

  this.after('initialize', function() {
    this.$traces = this.$node.find('.trace');
    this.$traces.each(function() {
      const $this = $(this);
      this.duration = parseInt($this.data('duration'), 10);
      this.timestamp = parseInt($this.data('timestamp'), 10);
      this.percentage = parseInt($this.data('servicePercentage'), 10);
    });
    this.on(document, 'uiUpdateTraceSortOrder', this.updateSortOrder);
    const sortOrderSelect = $('.sort-order');
    this.updateSortOrder(null, {order: sortOrderSelect.val()});
  });
});
