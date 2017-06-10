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

export default component(function traceFilters() {
  this.idFromService = function(service) {
    return `service-filter-${service.replace(/[^a-z0-9\-_]/gi, '-')}`;
  };

  this.addToFilter = function(ev, data) {
    if (this.$node.find(`[data-service-name='${data.value}']`).length) return;

    // TODO: should this be mustache instead?
    const $remove =
      $('<span>')
        .attr('class', 'badge service-filter-remove')
        .text('x')
        .on('click', function() { $(this).trigger('uiRemoveServiceNameFilter', data); });

    const $html =
      $('<span>')
        .attr('class', 'label service-filter-label service-tag-filtered')
        .attr('id', this.idFromService(data.value))
        .attr('data-serviceName', data.value)
        .text(data.value)
        .append($remove);

    this.$node.find('.service-tags').append($html);
  };

  this.removeFromFilter = function(ev, data) {
    this.$node.find(`#${this.idFromService(data.value)}`).remove();
  };

  this.updateTraces = function(ev, data) {
    this.$node.find('.filter-current').text(data.traces.size());
  };

  this.updateSortOrder = function(ev) {
    this.trigger(document, 'uiUpdateTraceSortOrder', {order: $(ev.target).val()});
  };

  this.after('initialize', function() {
    this.on(document, 'uiAddServiceNameFilter', this.addToFilter);
    this.on(document, 'uiRemoveServiceNameFilter', this.removeFromFilter);
    this.on(document, 'uiUpdateTraces', this.updateTraces);
    this.on('.sort-order', 'change', this.updateSortOrder);
  });
});
