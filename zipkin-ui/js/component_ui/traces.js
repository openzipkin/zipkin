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
import $ from 'jquery';
import FilterLabelUI from '../component_ui/filterLabel';

export default component(function traces() {
  this.$traces = [];
  this.services = [];

  this.triggerUpdateTraces = function() {
    this.$node.trigger('uiUpdateTraces', {traces: this.$traces.filter(':visible')});
  };

  this.updateTraces = function() {
    const services = this.services;
    this.$traces.each(function() {
      const $trace = $(this);
      if (services.length > 0) {
        let show = true;
        $.each(services, (idx, svc) => {
          if (!$trace.has(`.service-filter-label[data-service-name='${svc}']`).length) {
            show = false;
          }
        });

        $trace[show ? 'show' : 'hide']();
      } else {
        $trace.show();
      }
    });

    this.triggerUpdateTraces();
  };

  this.addFilter = function(ev, data) {
    if ($.inArray(data.value, this.services) === -1) {
      this.services.push(data.value);
      this.updateTraces();
    }
  };

  this.removeFilter = function(ev, data) {
    const idx = $.inArray(data.value, this.services);
    if (idx > -1) {
      this.services.splice(idx, 1);
      this.updateTraces();
    }
  };

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

      // Flight needs something like jQuery's $.live() functionality
      FilterLabelUI.teardownAll();
      FilterLabelUI.attachTo('.service-filter-label');

      this.triggerUpdateTraces();
    }
  };

  this.after('initialize', function() {
    FilterLabelUI.attachTo('.service-filter-label');

    this.$traces = this.$node.find('.trace');
    this.$traces.each(function() {
      const $this = $(this);
      this.duration = parseInt($this.data('duration'), 10);
      this.timestamp = parseInt($this.data('timestamp'), 10);
      this.percentage = parseInt($this.data('servicePercentage'), 10);
    });
    this.on(document, 'uiAddServiceNameFilter', this.addFilter);
    this.on(document, 'uiRemoveServiceNameFilter', this.removeFilter);
    this.on(document, 'uiUpdateTraceSortOrder', this.updateSortOrder);
    const sortOrderSelect = $('.sort-order');
    this.updateSortOrder(null, {order: sortOrderSelect.val()});
  });
});
