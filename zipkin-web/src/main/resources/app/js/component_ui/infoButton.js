'use strict';

define(
  [
    'flight'
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
