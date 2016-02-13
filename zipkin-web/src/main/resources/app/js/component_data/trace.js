import {component} from 'flight';
import $ from 'jquery';

export const TraceData = component(function traceData() {
  this.after('initialize', function() {
    $.ajax('/modelview' + window.location.pathname + window.location.search, {
      type: "GET",
      dataType: "json",
      context: this,
      success: function(modelview) {
        this.trigger('tracePageModelView', modelview);
      }
    });
  });
});
