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
      const apiURL = `/zipkin/api/v1/traces?${queryString.stringify(query)}`;
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
