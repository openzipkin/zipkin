'use strict';

define(
  [
    'flightjs'
  ],

  function (flight) {

    return flight.component(zoomOutSpans);

    function zoomOutSpans() {
      this.zoomOut = function() {
        this.trigger('uiZoomOutSpans');
      };

      this.after('initialize', function() {
        this.on('click', this.zoomOut);
      });
    }

  }
);

