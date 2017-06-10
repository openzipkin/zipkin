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
/* eslint-disable prefer-template */
import {component} from 'flightjs';
import chosen from 'chosen-npm/public/chosen.jquery.js'; // eslint-disable-line no-unused-vars
import $ from 'jquery';
import queryString from 'query-string';

export default component(function spanName() {
  this.updateSpans = function(ev, data) {
    this.render(data.spans);
    this.trigger('chosen:updated');
  };

  this.render = function(spans) {
    this.$node.empty();
    this.$node.append($($.parseHTML('<option value="all">all</option>')));

    const selectedSpanName = queryString.parse(window.location.search).spanName;
    $.each(spans, (i, span) => {
      const option = $($.parseHTML('<option/>'));
      option.val(span);
      option.text(span);
      if (span === selectedSpanName) {
        option.prop('selected', true);
      }
      this.$node.append(option);
    });
  };

  this.after('initialize', function() {
    this.$node.chosen({
      search_contains: true
    });
    this.on(document, 'dataSpanNames', this.updateSpans);
  });
});
