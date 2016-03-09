import {component} from 'flightjs';
import $ from 'jquery';

export default component(function serviceNames() {
  this.updateServiceNames = function(ev, lastServiceName) {
    $.ajax("/api/v1/services", {
      type: "GET",
      dataType: "json",
      success: serviceNames => {
        this.trigger('dataServiceNames', {serviceNames, lastServiceName});
      }
    });
  };

  this.after('initialize', function() {
    this.on('uiChangeServiceName', this.updateServiceNames);
  });
});
