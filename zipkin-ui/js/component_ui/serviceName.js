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
import Cookies from 'js-cookie';
import $ from 'jquery';
import chosen from 'chosen-npm/public/chosen.jquery.js'; // eslint-disable-line no-unused-vars
import queryString from 'query-string';

export default component(function serviceName() {
  this.onChange = function() {
    Cookies.set('last-serviceName', this.$node.val());
    this.triggerChange(this.$node.val());
  };

  this.triggerChange = function(name) {
    this.$node.trigger('uiChangeServiceName', name);
  };

  this.updateServiceNameDropdown = function(ev, data) {
    $('#serviceName').empty();
    $.each(data.names, (i, item) => {
      $('<option>').val(item).text(item).appendTo('#serviceName');
    });

    this.$node.find(`[value="${data.lastServiceName}"]`).attr('selected', 'selected');

    this.trigger('chosen:updated');

    // On the first view there won't be a selected or "last" service
    // name.  Instead the first service at the top of the list will be
    // displayed, so load the span names for the top service too.
    if (!data.lastServiceName && data.names && data.names.length > 1) {
      this.$node.trigger('uiFirstLoadSpanNames', data.names[0]);
    }
  };

  this.after('initialize', function() {
    const name = queryString.parse(window.location.search).serviceName
        || Cookies.get('last-serviceName');
    this.triggerChange(name);

    this.$node.chosen({search_contains: true});
    this.on('change', this.onChange);
    this.on(document, 'dataServiceNames', this.updateServiceNameDropdown);
  });
});
