'use strict';

define(
  [
    'flightjs'
  ],

  function (flight) {

    return flight.component(infoButton);

    function infoButton() {
      this.requestInfoPanel = function() {
        this.trigger('uiRequestInfoPanel');
      };

      this.after('initialize', function() {
        this.on('click', this.requestInfoPanel);
      });
    }

  }
);
