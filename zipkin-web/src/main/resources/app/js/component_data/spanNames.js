'use strict';

define(
  [
    'flight/lib/component'
  ],

  function (defineComponent) {

    return defineComponent(spanNames);

    function spanNames() {
      this.updateSpanNames = function(ev, serviceName) {
        $.ajax("/api/spans?serviceName="+serviceName, {
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

