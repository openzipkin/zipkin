import {component} from 'flightjs';
import $ from 'jquery';
import traceToMustache from '../../js/component_ui/traceToMustache';

export const TraceData = component(function traceData() {
  this.after('initialize', function() {
    $.ajax('/api/v1/trace/' + this.attr.traceId, {
      type: "GET",
      dataType: "json",
      context: this,
      success: (trace) => {
        const modelview = traceToMustache(trace);
        this.trigger('tracePageModelView', modelview);
      }
    });
  });
});
