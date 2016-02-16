'use strict';

define(
  [
    'flight'
  ],

  function (flight) {

    return flight.component(spanNames);

    function spanNames() {
      this.updateSpanNames = function(ev, serviceName) {
        $.ajax("/api/v1/spans?serviceName=" + serviceName, {
          type: "GET",
          dataType: "json",
          context: this,
          success: function(spans) {
            this.trigger('dataSpanNames', {spans: spans});
          }
        });
      };

      this.after('initialize', function() {
        this.on('uiChangeServiceName', this.updateSpanNames);
      });
    }

  }
);

