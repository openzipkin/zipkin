import {component} from 'flightjs';
import $ from 'jquery';
import {getError} from '../../js/component_ui/error';
import traceToMustache from '../../js/component_ui/traceToMustache';

export default component(function TraceData() {
  this.after('initialize', function() {
    $.ajax(`/api/v1/trace/${this.attr.traceId}`, {
      type: 'GET',
      dataType: 'json'
    }).done(trace => {
      const modelview = traceToMustache(trace);
      this.trigger('tracePageModelView', {modelview, trace});
    }).fail(e => {
      this.trigger('uiServerError',
                   getError(`Cannot load trace ${this.attr.traceId}`, e));
    });
  });
});
