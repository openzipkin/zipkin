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
import {errToStr} from '../../js/component_ui/error';
import $ from 'jquery';
import queryString from 'query-string';
import {traceSummary, traceSummariesToMustache} from '../component_ui/traceSummary';

export function convertToApiQuery(windowLocationSearch) {
  const query = queryString.parse(windowLocationSearch);
  // zipkin's api looks back from endTs
  if (query.startTs) {
    if (query.endTs > query.startTs) {
      query.lookback = String(query.endTs - query.startTs);
    }
    delete query.startTs;
  }
  return query;
}

export default component(function DefaultData() {
  this.after('initialize', function() {
    const query = convertToApiQuery(window.location.search);
    const serviceName = query.serviceName;
    if (serviceName) {
      const apiURL = `/api/v1/traces?${queryString.stringify(query)}`;
      $.ajax(apiURL, {
        type: 'GET',
        dataType: 'json'
      }).done(traces => {
        const modelview = {
          traces: traceSummariesToMustache(serviceName, traces.map(traceSummary)),
          apiURL,
          rawResponse: traces
        };
        this.trigger('defaultPageModelView', modelview);
      }).fail(e => {
        this.trigger('defaultPageModelView', {traces: [],
                                              queryError: errToStr(e)});
      });
    } else {
      this.trigger('defaultPageModelView', {traces: []});
    }
  });
});
