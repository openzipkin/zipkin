import {component} from 'flightjs';
import $ from 'jquery';
import queryString from 'query-string';
import {traceSummary, traceSummariesToMustache} from '../component_ui/traceSummary';

export default component(function DefaultData() {
  this.after('initialize', function() {
    const serviceName = queryString.parse(window.location.search).serviceName;
    if (serviceName) {
      $.ajax('/api/v1/traces' + window.location.search, {
        type: 'GET',
        dataType: 'json',
        success: traces => {
          const modelview = {traces: traceSummariesToMustache(serviceName, traces.map(traceSummary)) };
          this.trigger('defaultPageModelView', modelview);
        }
      });
    } else {
      this.trigger('defaultPageModelView', {traces: []});
    }
  });
});
