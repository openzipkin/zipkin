'use strict';

define(
  [
    'flightjs'
  ],

  function (flight) {

    return flight.component(serviceNames);

    function serviceNames() {
      this.updateServiceNames = function(ev, lastServiceName) {
        $.ajax("/api/v1/services", {
          type: "GET",
          dataType: "json",
          context: this,
          success: function(serviceNames) {
            this.trigger('dataServiceNames', {serviceNames: serviceNames, lastServiceName: lastServiceName});
          }
        });
      };

      this.after('initialize', function() {
        this.on('uiChangeServiceName', this.updateServiceNames);
      });
    }

  }
);
