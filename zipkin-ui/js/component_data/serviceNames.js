import {component} from 'flightjs';
import $ from 'jquery';

export default component(function serviceNames() {
  this.updateServiceNames = function(ev, lastServiceName) {
    $.ajax('/api/v1/services', {
      type: 'GET',
      dataType: 'json',
      success: names => {
        this.trigger('dataServiceNames', {names, lastServiceName});
      }
    });
  };

  this.after('initialize', function() {
    this.on('uiChangeServiceName', this.updateServiceNames);
  });
});
