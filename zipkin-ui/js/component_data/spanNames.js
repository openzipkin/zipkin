import {component} from 'flightjs';
import $ from 'jquery';

export default component(function spanNames() {
  this.updateSpanNames = function(ev, serviceName) {
    $.ajax(`/api/v1/spans?serviceName=${serviceName}`, {
      type: 'GET',
      dataType: 'json',
      success: spans => {
        this.trigger('dataSpanNames', {spans});
      }
    });
  };

  this.after('initialize', function() {
    this.on('uiChangeServiceName', this.updateSpanNames);
  });
});
