'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(serviceNames);

    function serviceNames() {
      this.updateServiceNames = function(ev, lastServiceName) {
        $.ajax("/api/services", {
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
