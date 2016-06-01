import {component} from 'flightjs';
import $ from 'jquery';
import traceToMustache from '../../js/component_ui/traceToMustache';

export default component(function TraceData() {
  this.after('initialize', function() {
    $.ajax(`/api/v1/trace/${this.attr.traceId}`, {
      type: 'GET',
      dataType: 'json',
      success: trace => {
        const modelview = traceToMustache(trace);
        this.trigger('tracePageModelView', {modelview, trace});
      }
    });
  });
});
